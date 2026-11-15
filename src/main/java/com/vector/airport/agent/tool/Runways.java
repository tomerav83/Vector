package com.vector.airport.agent.tool;

import static org.jooq.impl.DSL.val;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** The runways of airports in the FAA register, longest first. */
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
        List<Map<String, Object>> runways =
                db.fetch("""
                select airport_id, runway_id, length_ft, width_ft, surface
                from runway
                where airport_id = any({0})
                order by airport_id, length_ft desc
                """, val(codes.ids(airports).toArray(String[]::new))).intoMaps();
        return Evidence.create(
                db,
                runways,
                List.of("The register lacks the spacing of parallel runways, which decides whether both can be used in"
                        + " poor weather."),
                "faa-register");
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
