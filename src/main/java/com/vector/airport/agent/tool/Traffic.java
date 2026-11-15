package com.vector.airport.agent.tool;

import static org.jooq.impl.DSL.val;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jooq.DSLContext;
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

    /** The shortest flight that counts as long haul, in statute miles, unless the analyst says otherwise. */
    private static final int LONG_HAUL_MILES = 3000;

    private static final String SOURCE = "bts-segments";
    private static final String LATE_REPORTING = "The newest month can still lack late-reporting airlines.";

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
        List<Map<String, Object>> traffic = db.fetch(
                        "select * from traffic where airport_id = any({0}) order by airport_id",
                        val(codes.ids(airports).toArray(String[]::new)))
                .intoMaps();
        return Evidence.create(
                db,
                traffic,
                List.of(
                        LATE_REPORTING,
                        "Shortfall mixes capacity limits with weather, crew and airline cancellations."),
                SOURCE);
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
        List<Map<String, Object>> shares = db.fetch(
                        """
                with flights as (
                    select *, distance_mi >= {1} as long_haul
                    from recent_segment
                    where origin_id = any({0}))
                select origin_id as airport_id,
                       any_value(first_month) as first_month,
                       any_value(last_month) as last_month,
                       {1} as miles,
                       sum(departures_performed) as departures,
                       round(sum(departures_performed) filter (where long_haul) / sum(departures_performed), 4)
                           as long_haul_share,
                       sum(departures_performed) filter (where passenger) as passenger_departures,
                       round(sum(departures_performed) filter (where long_haul and passenger)
                             / passenger_departures, 4) as passenger_long_haul_share,
                       sum(departures_performed) filter (where not passenger) as cargo_departures,
                       round(sum(departures_performed) filter (where long_haul and not passenger)
                             / cargo_departures, 4) as cargo_long_haul_share
                from flights
                group by origin_id
                order by origin_id
                """,
                        val(codes.ids(airports).toArray(String[]::new)),
                        val(Objects.requireNonNullElse(miles, LONG_HAUL_MILES)))
                .intoMaps();
        return Evidence.create(
                db,
                shares,
                List.of(LATE_REPORTING, "Departures performed, scheduled and charter; distance is the nonstop leg."),
                SOURCE);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
