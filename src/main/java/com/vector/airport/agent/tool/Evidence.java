package com.vector.airport.agent.tool;

import static org.jooq.impl.DSL.val;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;

/**
 * What a tool answers with: its rows, the state of the sources they come from, and what the numbers cannot say, e.g.
 * that delays count only the airlines that report them.
 *
 * @param rows one per airport, each with the months it covers: a view's columns by name, or a value such as
 *     {@link Score}
 * @param sources each source's row in the {@code source} table: the period loaded, or the problem that left it out
 * @param caveats the limits of the data, for the answer to state
 */
record Evidence<T>(List<T> rows, List<Map<String, Object>> sources, List<String> caveats) {

    /**
     * The rows with the state of {@code sources}, e.g. {@code bts-segments}.
     *
     * @throws IllegalStateException naming a source the active profiles do not have, e.g. a misspelt one
     */
    static <T> Evidence<T> create(DSLContext db, List<T> rows, List<String> caveats, String... sources) {
        List<Map<String, Object>> states = db.fetch(
                        "select name, period, problem from source where name = any({0}) order by name", val(sources))
                .intoMaps();
        List<String> unknown = Arrays.stream(sources)
                .filter(source -> states.stream().noneMatch(state -> source.equals(state.get("name"))))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("No source %s in the active profiles".formatted(unknown));
        }
        return new Evidence<>(rows, states, caveats);
    }
}
