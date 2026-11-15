package com.vector.airport.data;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;

import com.vector.airport.data.format.Formats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jooq.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.conf.SettingsTools;
import org.jooq.impl.DSL;
import org.springframework.core.io.Resource;
import org.springframework.util.FileSystemUtils;

/**
 * One snapshot of the tables, built aside in its own DuckDB schema and then published: readers query {@code main},
 * which publishing points at this snapshot in one transaction, so a reader never sees a half-built snapshot
 * (blue-green). A snapshot that is never published is dropped when closed.
 *
 * <p>The sql folder runs in this order: {@code schema.sql} creates the tables, empty; each {@code <source>.sql} fills
 * its source's, in its own transaction, so a source that fails leaves nothing behind; {@code finish.sql} does what
 * needs several sources; publishing runs {@code views.sql} in {@code main}, where the readers are. In a source's SQL,
 * {@code {file}} is a downloaded file and {@code {file:ENTRY}} one entry of a downloaded zip, each an SQL string
 * literal; a source checks what it needs with DuckDB's {@code error()}, which fails its load.
 */
final class Tables implements AutoCloseable {

    private static final String MAIN = "main";
    private static final String SNAPSHOT = "snapshot_";
    private static final String SCHEMA = "schema";
    private static final String FINISH = "finish";
    private static final String VIEWS = "views";

    private final DSLContext db;
    private final ConnectionProvider pool;
    private final Connection connection;
    private final String schema;
    private final Formats formats;

    /** Each SQL file of the folder by name: {@code schema}, {@code finish}, {@code views} and one per source. */
    private final Map<String, Script> scripts;

    private boolean published;

    private Tables(
            DSLContext db,
            ConnectionProvider pool,
            Connection connection,
            String schema,
            Formats formats,
            Map<String, Script> scripts) {
        this.db = db;
        this.pool = pool;
        this.connection = connection;
        this.schema = schema;
        this.formats = formats;
        this.scripts = scripts;
    }

    /**
     * A new snapshot's tables, empty, on a connection of {@code pool} until closed.
     *
     * @param folder the folder of {@code schema.sql}, {@code finish.sql}, {@code views.sql} and each
     *     {@code <source>.sql}
     * @throws IllegalStateException when one of them is missing
     */
    static Tables create(DSLContext db, Formats formats, Resource folder, Collection<String> sources) {
        Map<String, Script> scripts = Stream.concat(Stream.of(SCHEMA, FINISH, VIEWS), sources.stream())
                .collect(toMap(identity(), name -> Script.read(folder, name)));
        ConnectionProvider pool = db.configuration().connectionProvider();
        Connection connection = pool.acquire();
        // One connection for the whole build, since the current schema is a setting of the connection. After a failed
        // statement DuckDB answers a read of its warnings with SQLSTATE 08003, "connection does not exist", and Hikari
        // would close the connection the whole build runs on.
        DSLContext snapshot = DSL.using(
                connection, db.dialect(), SettingsTools.clone(db.settings()).withFetchWarnings(false));
        Tables tables = new Tables(snapshot, pool, connection, SNAPSHOT + System.nanoTime(), formats, scripts);
        try {
            tables.createSchema();
            return tables;
        } catch (RuntimeException e) {
            tables.close();
            throw e;
        }
    }

    /**
     * Fills the source's tables from {@code version}, with its row in {@code source}, in one transaction.
     *
     * @param matchesLock every file is the one the lock records
     * @param problems why the attempts before this one failed, e.g. the newest period's HTTP 503; empty when none did
     * @return rows its SQL wrote
     * @throws RuntimeException when its SQL fails (a check, a renamed column) or writes no rows
     */
    long load(String source, Version version, boolean matchesLock, List<String> problems) {
        return db.transactionResult(configuration -> {
            DSLContext tx = configuration.dsl();
            Path work = Files.createTempDirectory("vector-" + source);
            try {
                long rows = version.files().stream()
                        .mapToLong(file -> render(source, file.path(), work).execute(tx))
                        .sum();
                if (rows == 0) {
                    throw new IllegalStateException("%s wrote no rows".formatted(version.period()));
                }
                tx.execute(
                        "insert into source (name, period, rows, matches_lock, problem)"
                                + " values (?, ?, ?, ?, nullif(?, ''))",
                        source,
                        version.period(),
                        rows,
                        matchesLock,
                        problem(problems));
                return rows;
            } finally {
                FileSystemUtils.deleteRecursively(work.toFile());
            }
        });
    }

    /** Records that nothing of the source is loaded, and why each attempt failed. */
    void leaveOut(String source, List<String> problems) {
        db.execute("insert into source (name, problem) values (?, ?)", source, problem(problems));
    }

    /** After every source: {@code finish.sql}, what needs several sources at once. */
    void finish() {
        db.transaction(configuration -> scripts.get(FINISH).execute(configuration.dsl()));
    }

    /**
     * Points {@code main} at this snapshot's tables, runs {@code views.sql} there and drops the snapshot before, in one
     * transaction.
     */
    void publish() {
        db.transaction(configuration -> {
            DSLContext tx = configuration.dsl();
            tx.fetch(
                            "select table_name from information_schema.tables"
                                    + " where table_catalog = current_database() and table_schema = ? order by 1",
                            schema)
                    .getValues(0, String.class)
                    .forEach(table -> tx.execute(
                            "create or replace view {0} as select * from {1}", name(MAIN, table), name(schema, table)));
            tx.connection(c -> c.setSchema(MAIN));
            scripts.get(VIEWS).execute(tx);
            tx.fetch(
                            "select schema_name from information_schema.schemata"
                                    + " where catalog_name = current_database()"
                                    + " and starts_with(schema_name, ?) and schema_name <> ?",
                            SNAPSHOT,
                            schema)
                    .getValues(0, String.class)
                    .forEach(old -> tx.execute("drop schema {0} cascade", name(old)));
        });
        published = true;
    }

    /** Drops this snapshot unless it was published, and gives the connection back to the pool, set to {@code main}. */
    @Override
    public void close() {
        try {
            db.connection(c -> c.setSchema(MAIN));
            if (!published) {
                db.execute("drop schema if exists {0} cascade", name(schema));
            }
        } finally {
            pool.release(connection);
        }
    }

    /** This snapshot's schema, made current, with the {@code source} table and the tables of {@code schema.sql}. */
    private void createSchema() {
        db.execute("create schema {0}", name(schema));
        db.connection(c -> c.setSchema(schema));
        db.execute("""
                create table source (
                    name         varchar primary key,
                    period       varchar, -- NULL: nothing loaded
                    rows         bigint,  -- what its SQL wrote
                    matches_lock boolean, -- every file is the one the lock records; false: upstream changed it since
                    problem      varchar  -- why nothing loaded, or why the attempts before the one that loaded failed
                )""");
        scripts.get(SCHEMA).execute(db);
    }

    /** The source's SQL for one of its files: {@code {file}} is the file, {@code {file:ENTRY}} that entry of it. */
    private Script render(String source, Path file, Path work) {
        Script script = scripts.get(source);
        return script.render((placeholder, entry) ->
                db.render(inline(input(script, placeholder, file, entry, work).toString())));
    }

    private Path input(Script script, String placeholder, Path file, Optional<String> entry, Path work) {
        if (!placeholder.equals("file")) {
            throw new IllegalArgumentException("Unknown placeholder {%s} in %s; there is {file} and {file:ENTRY}"
                    .formatted(placeholder, script.name()));
        }
        return entry.map(name -> extract(file, name, work)).orElse(file);
    }

    private Path extract(Path file, String entry, Path work) {
        try {
            return formats.detect(file).extract(file, entry, work);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Each attempt's problem in order, e.g. {@code HTTP 503; needs faa-register}. */
    private static String problem(List<String> problems) {
        return String.join("; ", problems);
    }
}
