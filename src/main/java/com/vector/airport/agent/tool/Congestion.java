package com.vector.airport.agent.tool;

import static com.vector.airport.view.tables.Congestion.CONGESTION;

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
        return db.transactionResult(_ -> {
            List<Map<String, Object>> delays = db.selectFrom(CONGESTION)
                    .where(CONGESTION.AIRPORT_ID.in(codes.ids(airports)))
                    .orderBy(CONGESTION.AIRPORT_ID)
                    .fetchMaps();
            return Evidence.create(
                    db,
                    delays,
                    List.of(
                            "NAS delays are those the National Airspace System causes: the airfield, the airspace and air"
                                    + " traffic control. A NAS share well above other airports' points there, not at the"
                                    + " terminal."),
                    List.of(
                            "Delays cover only the airlines that report on-time data to BTS, the largest US carriers; an"
                                    + " airport they do not serve has no delay figures."),
                    "bts-delays");
        });
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
