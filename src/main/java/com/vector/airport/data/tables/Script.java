package com.vector.airport.data.tables;

import static org.jooq.impl.DSL.raw;

import com.vector.airport.data.source.Template;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import org.jooq.DSLContext;
import org.springframework.core.io.Resource;

/**
 * One SQL file of the sql folder, e.g. {@code faa-register.sql}, run a statement at a time, so the rows each writes add
 * up and DuckDB's error points at the line in that statement; a missing file fails naming it.
 *
 * @param name its file name, e.g. {@code faa-register.sql}
 * @param text its SQL, where a statement ends with {@code ;} at the end of a line, or before a {@code --} comment there
 */
public record Script(String name, String text) {

    /** @throws IllegalStateException when {@code folder} has no {@code <name>.sql} */
    public static Script read(Resource folder, String name) {
        String file = name + ".sql";
        try {
            return new Script(file, folder.createRelative(file).getContentAsString(StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Missing SQL file %s in %s".formatted(file, folder), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** This script with each placeholder replaced by what {@code resolver} returns for its name and format. */
    Script render(BiFunction<String, Optional<String>, String> resolver) {
        return new Script(name, Template.render(text, resolver));
    }

    /**
     * Executes each statement as written, as jOOQ raw SQL: jOOQ's plain SQL templating would drop the braces of a
     * DuckDB struct such as {@code {'a': 1}}. The rows they wrote, where DDL, which JDBC answers with -1, writes none.
     */
    public long execute(DSLContext db) {
        return Arrays.stream(text.split(";[ \\t]*(?:--[^\\n]*)?\\r?\\n"))
                .filter(Script::hasCode)
                .mapToLong(statement -> Math.max(0, db.execute(raw(statement))))
                .sum();
    }

    /** False for a chunk of only comments, such as a header or what follows the last statement. */
    private static boolean hasCode(String chunk) {
        return chunk.lines().map(String::strip).anyMatch(line -> !line.isEmpty() && !line.startsWith("--"));
    }
}
