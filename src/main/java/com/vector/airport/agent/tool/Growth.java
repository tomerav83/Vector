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

/** The growth the FAA Terminal Area Forecast expects at airports, as the {@code growth} view has it. */
@Component
class Growth implements ToolCallbackProvider {

    private final DSLContext db;
    private final AirportCodes codes;

    Growth(DSLContext db, AirportCodes codes) {
        this.db = db;
        this.codes = codes;
    }

    @Tool(description = """
            The FAA Terminal Area Forecast for airports: boardings and operations (takeoffs and landings) in its base \
            year, the newest fiscal year of actuals, and in the forecast year it names, the boardings added between \
            the two, and the yearly growth rate.""")
    Evidence<Map<String, Object>> growth(@ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports) {
        List<Map<String, Object>> growth = db.fetch(
                        "select * from growth where airport_id = any({0}) order by airport_id",
                        val(codes.ids(airports).toArray(String[]::new)))
                .intoMaps();
        return Evidence.create(
                db,
                growth,
                List.of("FAA fiscal years run October to September.", "A forecast is the FAA's planning baseline."),
                "faa-forecast");
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
