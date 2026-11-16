package com.vector.airport.agent.tool;

import static com.vector.airport.view.tables.Airport.AIRPORT;
import static com.vector.airport.view.tables.AirportCode.AIRPORT_CODE;
import static com.vector.airport.view.tables.CommercialAirport.COMMERCIAL_AIRPORT;
import static com.vector.airport.view.tables.Metro.METRO;
import static java.util.function.Predicate.not;
import static org.jooq.impl.DSL.concatWs;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The US airports with commercial service, found by code, name, city, metro area, state or hub size: the tool that
 * gives the codes; and how many airports the data has.
 */
@Component
class Airports implements ToolCallbackProvider {

    /** The most airports one search answers with, busiest first. */
    private static final int MAX_RESULTS = 50;

    private final DSLContext db;

    Airports(DSLContext db) {
        this.db = db;
    }

    @Tool(
            description = "Finds US commercial-service airports by code (FAA, ICAO or airline), name or city, with"
                    + " every airport of a city's FAA metro area (New York gives JFK, LGA and EWR), and/or by state"
                    + " and FAA hub size, busiest first, at most " + MAX_RESULTS + ": the code the other tools take,"
                    + " the airline code, name, city, state, FAA hub size, boardings in the newest year, and the main"
                    + " airport of its metro area. Call it first to turn names like Logan into codes.")
    Evidence<Map<String, Object>> find(
            @ToolParam(required = false, description = "a code, name or city, e.g. KSFO, Logan, Santa Ana")
                    String query,
            @ToolParam(required = false, description = "two-letter states, e.g. [MA, NH]") List<String> states,
            @ToolParam(required = false, description = "FAA hub sizes, e.g. [LARGE]") List<Hub> hubs) {
        Condition named = Optional.ofNullable(query)
                .map(String::strip)
                .filter(not(String::isEmpty))
                .map(Airports::named)
                .orElse(noCondition());
        Condition located = Optional.ofNullable(states)
                .filter(not(List::isEmpty))
                .map(Airports::located)
                .orElse(noCondition());
        Condition sized = Optional.ofNullable(hubs)
                .filter(not(List::isEmpty))
                .map(given ->
                        COMMERCIAL_AIRPORT.HUB.in(given.stream().map(Hub::name).toList()))
                .orElse(noCondition());
        return db.transactionResult(_ -> {
            List<Map<String, Object>> airports = db.selectFrom(COMMERCIAL_AIRPORT)
                    .where(named, located, sized)
                    .orderBy(COMMERCIAL_AIRPORT.BOARDINGS.desc().nullsLast())
                    .limit(MAX_RESULTS)
                    .fetchMaps();
            return Evidence.create(
                    db,
                    airports,
                    List.of(),
                    List.of(),
                    "bts-airports",
                    "faa-boardings",
                    "faa-forecast",
                    "faa-register");
        });
    }

    @Tool(
            description = "Counts the US airports in the data, in every state or in the given ones: the FAA"
                    + " register's airports and seaplane bases, how many are open, and those with commercial service"
                    + " by FAA hub size; primary airports have 10,000+ boardings a year. Call it for any how-many"
                    + " question; never count rows of another tool.")
    Evidence<Map<String, Object>> count(
            @ToolParam(required = false, description = "two-letter states, e.g. [TX]; none for every state")
                    List<String> states) {
        Condition located = Optional.ofNullable(states)
                .filter(not(List::isEmpty))
                .map(given -> AIRPORT.STATE.in(upperCase(given)))
                .orElse(noCondition());
        return db.transactionResult(_ -> Evidence.create(
                db,
                db.select(
                                airports(noCondition()).as("airports"),
                                airports(AIRPORT.CLOSED.isFalse()).as("open_airports"),
                                airports(AIRPORT.HUB.isNotNull()).as("commercial_airports"),
                                airports(AIRPORT.HUB.in(Arrays.stream(Hub.values())
                                                .map(Hub::name)
                                                .toList()))
                                        .as("primary_airports"),
                                airports(AIRPORT.HUB.eq(Hub.LARGE.name())).as("large_hubs"),
                                airports(AIRPORT.HUB.eq(Hub.MEDIUM.name())).as("medium_hubs"),
                                airports(AIRPORT.HUB.eq(Hub.SMALL.name())).as("small_hubs"),
                                airports(AIRPORT.HUB.eq(Hub.NONHUB.name())).as("nonhubs"),
                                airports(AIRPORT.HUB.eq("NONPRIMARY")).as("nonprimary_airports"))
                        .from(AIRPORT)
                        .where(located)
                        .fetchMaps(),
                List.of(),
                List.of(),
                "faa-boardings",
                "faa-register"));
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }

    /**
     * The airports with {@code query} as a code, or in their name or city, or in the metro area of a main airport with
     * it in its city (Newark's EWR for New York), in any case and taken literally.
     */
    private static Condition named(String query) {
        return COMMERCIAL_AIRPORT
                .AIRPORT_ID
                .in(select(AIRPORT_CODE.AIRPORT_ID)
                        .from(AIRPORT_CODE)
                        .where(AIRPORT_CODE.CODE.eq(query.toUpperCase(Locale.ROOT))))
                .or(concatWs(" ", COMMERCIAL_AIRPORT.NAME, COMMERCIAL_AIRPORT.DISPLAY_NAME, COMMERCIAL_AIRPORT.CITY)
                        .containsIgnoreCase(query))
                .or(COMMERCIAL_AIRPORT.AIRPORT_ID.in(select(METRO.AIRPORT_ID)
                        .from(METRO)
                        .innerJoin(AIRPORT)
                        .on(METRO.HUB_ID.eq(AIRPORT.AIRPORT_ID))
                        .where(AIRPORT.CITY.containsIgnoreCase(query))));
    }

    /** The airports in {@code states}, in any case. */
    private static Condition located(List<String> states) {
        return COMMERCIAL_AIRPORT.STATE.in(upperCase(states));
    }

    private static String[] upperCase(List<String> states) {
        return states.stream().map(state -> state.toUpperCase(Locale.ROOT)).toArray(String[]::new);
    }

    /** How many airports of the register {@code kept} keeps, e.g. the open ones. */
    private static Field<Integer> airports(Condition kept) {
        return DSL.count().filterWhere(kept);
    }
}
