package com.vector.airport.agent.tool;

import static com.vector.airport.view.tables.RecentSegment.RECENT_SEGMENT;
import static com.vector.airport.view.tables.Traffic.TRAFFIC;
import static org.jooq.impl.DSL.anyValue;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.nullif;
import static org.jooq.impl.DSL.round;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The traffic departing airports over the newest 12 months of BTS T-100 segments: how full the flights are (the
 * {@code traffic} view) and how far they fly.
 */
@Component
class Traffic implements ToolCallbackProvider {

    private static final String LATE_REPORTING =
            "The latest month is preliminary: airlines that file late are added in later releases.";
    private static final String SOURCE = "bts-segments";

    /** The shortest flight that counts as long haul, in statute miles, unless the analyst says otherwise. */
    private static final int LONG_HAUL_MILES = 3000;

    private final DSLContext db;
    private final AirportCodes codes;

    Traffic(DSLContext db, AirportCodes codes) {
        this.db = db;
        this.codes = codes;
    }

    @Tool(description = """
            Departing traffic of airports over the newest 12 months of BTS T-100 data: passengers, seats, load factor \
            (passengers / seats on scheduled passenger flights), departures scheduled and performed, and shortfall (the \
            share of scheduled departures not flown). Full flights and a shortfall are signs of unserved demand.""")
    Evidence<Map<String, Object>> traffic(@ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports) {
        return db.transactionResult(_ -> {
            List<Map<String, Object>> traffic = db.selectFrom(TRAFFIC)
                    .where(TRAFFIC.AIRPORT_ID.in(codes.ids(airports)))
                    .orderBy(TRAFFIC.AIRPORT_ID)
                    .fetchMaps();
            return Evidence.create(
                    db,
                    traffic,
                    List.of("Departures not flown are scheduled departures that were not operated."),
                    List.of(
                            LATE_REPORTING,
                            "Departures not flown do not say why: capacity limits, weather, crews and airline"
                                    + " cancellations all count."),
                    SOURCE);
        });
    }

    @Tool(description = """
            Share of airports' departures over the newest 12 months of BTS T-100 data that fly at least the given \
            distance nonstop: for all flights, passenger flights and cargo flights, each with its number of departures.""")
    Evidence<Map<String, Object>> longHaul(
            @ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports,
            @ToolParam(
                            required = false,
                            description = "the shortest long-haul flight in statute miles; " + LONG_HAUL_MILES
                                    + " unless the user says otherwise")
                    Integer miles) {
        int shortest = Objects.requireNonNullElse(miles, LONG_HAUL_MILES);
        Condition far = RECENT_SEGMENT.DISTANCE_MI.ge((double) shortest);
        Condition passenger = RECENT_SEGMENT.PASSENGER.isTrue();
        Condition cargo = RECENT_SEGMENT.PASSENGER.isFalse();
        return db.transactionResult(_ -> {
            List<Map<String, Object>> shares = db.select(
                            RECENT_SEGMENT.ORIGIN_ID.as("airport_id"),
                            // the same on every row of a recent() table
                            anyValue(RECENT_SEGMENT.FIRST_MONTH).as("first_month"),
                            anyValue(RECENT_SEGMENT.LAST_MONTH).as("last_month"),
                            inline(shortest).as("miles"),
                            departures(noCondition()).as("departures"),
                            share(far, noCondition()).as("long_haul_share"),
                            departures(passenger).as("passenger_departures"),
                            share(far, passenger).as("passenger_long_haul_share"),
                            departures(cargo).as("cargo_departures"),
                            share(far, cargo).as("cargo_long_haul_share"))
                    .from(RECENT_SEGMENT)
                    .where(RECENT_SEGMENT.ORIGIN_ID.in(codes.ids(airports)))
                    .groupBy(RECENT_SEGMENT.ORIGIN_ID)
                    .orderBy(RECENT_SEGMENT.ORIGIN_ID)
                    .fetchMaps();
            return Evidence.create(
                    db,
                    shares,
                    List.of("Departures are flights operated, scheduled and charter; distance is the nonstop length of"
                            + " each leg."),
                    List.of(LATE_REPORTING),
                    SOURCE);
        });
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }

    /** The departures performed on the flights {@code flights} keeps, e.g. the passenger flights. */
    private static Field<Long> departures(Condition flights) {
        return sum(RECENT_SEGMENT.DEPARTURES_PERFORMED).filterWhere(flights).cast(Long.class);
    }

    /** The share of {@code flights}' departures that fly {@code far}, to 4 decimals; NULL without departures. */
    private static Field<Double> share(Condition far, Condition flights) {
        Field<BigDecimal> departures = sum(RECENT_SEGMENT.DEPARTURES_PERFORMED).filterWhere(flights);
        return round(
                        sum(RECENT_SEGMENT.DEPARTURES_PERFORMED)
                                .filterWhere(far.and(flights))
                                .div(nullif(departures, BigDecimal.ZERO)),
                        4)
                .cast(Double.class);
    }
}
