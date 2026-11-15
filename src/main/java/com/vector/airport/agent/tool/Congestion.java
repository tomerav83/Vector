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

/**
 * The arrival delays at airports over the newest 12 months of BTS on-time data, as the {@code congestion} view has
 * them.
 */
@Component
class Congestion implements ToolCallbackProvider {

    private final DSLContext db;
    private final AirportCodes codes;

    Congestion(DSLContext db, AirportCodes codes) {
        this.db = db;
        this.codes = codes;
    }

    @Tool(description = """
            Arrival delays at airports over the newest 12 months of BTS on-time data: the share of arrivals 15+ minutes \
            late, the same share for the whole US, cancelled and diverted shares, minutes per delayed arrival, and how \
            the delay minutes split by cause: carrier, weather, NAS (the air traffic system: airport and airspace \
            capacity, non-extreme weather), security, and late-arriving aircraft.""")
    Evidence<Map<String, Object>> congestion(@ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports) {
        List<Map<String, Object>> delays = db.fetch(
                        "select * from congestion where airport_id = any({0}) order by airport_id",
                        val(codes.ids(airports).toArray(String[]::new)))
                .intoMaps();
        return Evidence.create(
                db,
                delays,
                List.of(
                        "Only airlines that report on-time data (the largest US carriers) count; an airport they do not"
                                + " serve has no row.",
                        "A NAS share well above other airports' points at the airfield or airspace, not the terminal."),
                "bts-delays");
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
