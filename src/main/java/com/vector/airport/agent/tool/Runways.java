package com.vector.airport.agent.tool;

import static com.vector.airport.view.tables.CommercialAirport.COMMERCIAL_AIRPORT;
import static com.vector.airport.view.tables.Runway.RUNWAY;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The airfield of airports: their runways in the FAA register, longest first, and the limit the FAA sets on flights.
 */
@Component
class Runways implements ToolCallbackProvider {

    private final DSLContext db;
    private final AirportCodes codes;

    Runways(DSLContext db, AirportCodes codes) {
        this.db = db;
        this.codes = codes;
    }

    @Tool(
            description =
                    "Runways of airports from the FAA register: id (e.g. 10L/28R), length and width in feet, surface.")
    Evidence<Map<String, Object>> runways(@ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports) {
        return db.transactionResult(_ -> {
            List<Map<String, Object>> runways = db.selectFrom(RUNWAY)
                    .where(RUNWAY.AIRPORT_ID.in(codes.ids(airports)))
                    .orderBy(RUNWAY.AIRPORT_ID, RUNWAY.LENGTH_FT.desc())
                    .fetchMaps();
            return Evidence.create(
                    db,
                    runways,
                    List.of(),
                    List.of("The register does not record the spacing between parallel runways, which decides whether"
                            + " both can operate in poor weather."),
                    "faa-register");
        });
    }

    @Tool(
            description = "The limit the FAA sets on the flights of airports with airline service: CAPPED, MANAGED,"
                    + " TERMINAL or NONE, defined in the answer. Call it for an airport's room to grow, beside rank.")
    Evidence<Map<String, Object>> flightLimits(
            @ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports) {
        return db.transactionResult(_ -> Evidence.create(
                db,
                db.select(COMMERCIAL_AIRPORT.AIRPORT_ID, COMMERCIAL_AIRPORT.FLIGHT_LIMIT)
                        .from(COMMERCIAL_AIRPORT)
                        .where(COMMERCIAL_AIRPORT.AIRPORT_ID.in(codes.ids(airports)))
                        .orderBy(COMMERCIAL_AIRPORT.AIRPORT_ID)
                        .fetchMaps(),
                List.of(
                        "CAPPED: airlines need an FAA slot for every flight, so they can't add flights; growth must come"
                                + " from bigger or fuller planes.",
                        "MANAGED: the FAA fits airlines' schedules around the busiest hours; flights can grow, mostly"
                                + " outside them.",
                        "TERMINAL: the airport fits international flights around its international terminal's busiest"
                                + " hours; other flights are not limited.",
                        "NONE: the FAA lists no limit, so airlines can add flights as demand grows."),
                List.of(
                        "An airport the FAA does not list has no federal limit on flights: one exists only by FAA rule"
                                + " or order.",
                        "Local limits, such as a passenger cap or a night curfew, are not covered."),
                "faa-slots-capped",
                "faa-slots-managed"));
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
