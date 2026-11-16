package com.vector.airport.agent.tool;

import static com.vector.airport.view.tables.Source.SOURCE;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;

/**
 * What a tool answers with: its rows, the sources they come from, what the figures count, e.g. that fiscal years run
 * October to September, and what they cannot say, e.g. that delays count only the airlines that report them.
 *
 * <p>A tool reads its rows and calls {@link #create} inside one {@code db.transactionResult}, so a publish in between
 * cannot label old rows with the new sources; the injected {@code DSLContext} joins that transaction.
 *
 * @param rows one per airport, each with the months it covers: a view's columns by name, or a value such as
 *     {@code Score}
 * @param sources each source's row in the {@code source} table: its title and page, and the period loaded or the
 *     problem that left it out
 * @param definitions what the figures count, in full sentences
 * @param limitations what the figures cannot say, in full sentences, for the answer to state
 */
public record Evidence<T>(
        List<T> rows, List<Map<String, Object>> sources, List<String> definitions, List<String> limitations) {

    /**
     * The rows with the state of {@code sources}, e.g. {@code bts-segments}.
     *
     * @throws IllegalStateException naming a source the active profiles do not have, e.g. a misspelt one
     */
    public static <T> Evidence<T> create(
            DSLContext db, List<T> rows, List<String> definitions, List<String> limitations, String... sources) {
        List<Map<String, Object>> states = db.select(
                        SOURCE.NAME, SOURCE.TITLE, SOURCE.PAGE, SOURCE.PERIOD, SOURCE.PROBLEM)
                .from(SOURCE)
                .where(SOURCE.NAME.in(sources))
                .orderBy(SOURCE.NAME)
                .fetchMaps();
        List<String> unknown = Arrays.stream(sources)
                .filter(source -> states.stream().noneMatch(state -> source.equals(state.get("name"))))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("No source %s in the active profiles".formatted(unknown));
        }
        return new Evidence<>(rows, states, definitions, limitations);
    }
}
