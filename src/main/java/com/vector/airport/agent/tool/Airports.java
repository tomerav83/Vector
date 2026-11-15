package com.vector.airport.agent.tool;

import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.val;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jooq.DSLContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** The US airports with commercial service, found by code, name, city or state: the tool that gives the codes. */
@Component
class Airports implements ToolCallbackProvider {

    /** The most airports one search answers with, busiest first. */
    private static final int MAX_RESULTS = 50;

    private final DSLContext db;

    Airports(DSLContext db) {
        this.db = db;
    }

    @Tool(
            description = "Finds US commercial-service airports by code (FAA, ICAO or airline), name or city, and/or by"
                    + " state, busiest first, at most " + MAX_RESULTS + ": the code the other tools take, the airline"
                    + " code, name, city, state, FAA hub size, boardings in the newest year, and the main airport of"
                    + " its metro area. Call it first to turn names like Logan into codes.")
    Evidence<Map<String, Object>> find(
            @ToolParam(required = false, description = "a code, name or city, e.g. KSFO, Logan, Santa Ana")
                    String query,
            @ToolParam(required = false, description = "two-letter states, e.g. [MA, NH]") List<String> states) {
        List<Map<String, Object>> airports = db.fetch(
                        """
                select a.airport_id, a.iata, a.name, a.city, a.state, a.hub, b.year as boardings_year, b.boardings,
                       string_agg(m.hub_id, ', ') as metro
                from airport a
                left join boardings b on b.airport_id = a.airport_id and b.year = (select max(year) from boardings)
                left join metro m on m.airport_id = a.airport_id
                where a.hub is not null
                  and (len({1}) = 0 or list_contains({1}, a.state))
                  and ({0} = '' or a.airport_id in (select airport_id from airport_code where code = upper({0}))
                       or concat_ws(' ', a.name, a.display_name, a.city) ilike '%' || {0} || '%')
                group by all
                order by b.boardings desc nulls last
                limit {2}
                """,
                        val(Objects.requireNonNullElse(query, "").strip()),
                        val(Objects.requireNonNullElse(states, List.<String>of()).stream()
                                .map(state -> state.toUpperCase(Locale.ROOT))
                                .toArray(String[]::new)),
                        inline(MAX_RESULTS))
                .intoMaps();
        return Evidence.create(
                db, airports, List.of(), "bts-airports", "faa-boardings", "faa-forecast", "faa-register");
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
