package com.vector.airport.data.tables;

import static com.vector.airport.view.tables.Source.SOURCE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.jooq.impl.DSL.asterisk;
import static org.jooq.impl.DSL.currentCatalog;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.nullif;
import static org.jooq.impl.DSL.select;

import com.vector.airport.data.cache.Version;
import com.vector.airport.data.format.Formats;
import com.vector.airport.data.source.Source;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jooq.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Meta;
import org.jooq.Named;
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
public final class Tables implements AutoCloseable {

    private static final String SCHEMA = "schema";
    private static final String FINISH = "finish";
    private static final String VIEWS = "views";
    private static final String SNAPSHOT = "snapshot_";
    private static final String MAIN = "main";

    private final DSLContext db;
    private final ConnectionProvider pool;
    private final Connection connection;
    private final String schema;
    private final Formats formats;
    private final Map<String, Source> sources;

    /** Each SQL file of the folder by name: {@code schema}, {@code finish}, {@code views} and one per source. */
    private final Map<String, Script> scripts;

    private boolean published;

    private Tables(
            DSLContext db,
            ConnectionProvider pool,
            Connection connection,
            String schema,
            Formats formats,
            Map<String, Source> sources,
            Map<String, Script> scripts) {
        this.db = db;
        this.pool = pool;
        this.connection = connection;
        this.schema = schema;
        this.formats = formats;
        this.sources = sources;
        this.scripts = scripts;
    }

    /**
     * A new snapshot's tables, empty, on a connection of {@code pool} until closed.
     *
     * @param sources every source by name, whose row in {@code source} cites it
     * @param folder the folder of {@code schema.sql}, {@code finish.sql}, {@code views.sql} and each
     *     {@code <source>.sql}
     * @throws IllegalStateException when one of them is missing
     */
    public static Tables create(DSLContext db, Formats formats, Resource folder, Map<String, Source> sources) {
        Map<String, Script> scripts = Stream.concat(Stream.of(SCHEMA, FINISH, VIEWS), sources.keySet().stream())
                .collect(toMap(identity(), name -> Script.read(folder, name)));
        ConnectionProvider pool = db.configuration().connectionProvider();
        Connection connection = pool.acquire();
        // One connection for the whole build, since the current schema is a setting of the connection. After a failed
        // statement DuckDB answers a read of its warnings with SQLSTATE 08003, "connection does not exist", and Hikari
        // would close the connection the whole build runs on.
        DSLContext snapshot = DSL.using(
                connection, db.dialect(), SettingsTools.clone(db.settings()).withFetchWarnings(false));
        Tables tables = new Tables(snapshot, pool, connection, SNAPSHOT + System.nanoTime(), formats, sources, scripts);
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
    public long load(String source, Version version, boolean matchesLock, List<String> problems) {
        return db.transactionResult(configuration -> {
            DSLContext tx = configuration.dsl();
            // In the cache beside the files: the only folder a query may read (allowed_directories, application.yml).
            Path work =
                    Files.createTempDirectory(version.files().getFirst().path().getParent(), source);
            try {
                long rows = version.files().stream()
                        .mapToLong(file -> render(source, file.path(), work).execute(tx))
                        .sum();
                if (rows == 0) {
                    throw new IllegalStateException("%s wrote no rows".formatted(version.period()));
                }
                row(tx, source)
                        .set(SOURCE.PERIOD, version.period())
                        .set(SOURCE.ROWS, rows)
                        .set(SOURCE.MATCHES_LOCK, matchesLock)
                        .set(SOURCE.PROBLEM, nullif(problem(problems), ""))
                        .execute();
                return rows;
            } finally {
                FileSystemUtils.deleteRecursively(work.toFile());
            }
        });
    }

    /** Records that nothing of the source is loaded, and why each attempt failed. */
    public void leaveOut(String source, List<String> problems) {
        row(db, source).set(SOURCE.PROBLEM, problem(problems)).execute();
    }

    /** After every source: {@code finish.sql}, what needs several sources at once. */
    public void finish() {
        db.transaction(configuration -> scripts.get(FINISH).execute(configuration.dsl()));
    }

    /**
     * Points {@code main} at this snapshot's tables, runs {@code views.sql} there and drops the snapshot before, in one
     * transaction.
     */
    public void publish() {
        db.transaction(configuration -> {
            DSLContext tx = configuration.dsl();
            // This database's schemas only: an attached database has its own snapshot_ schemas.
            String catalog = tx.fetchValue(select(currentCatalog()));
            Meta meta = tx.meta().filterCatalogs(c -> c.getName().equals(catalog));
            meta.getSchemas(schema)
                    .getFirst()
                    .getTables()
                    .forEach(table -> tx.createOrReplaceView(name(MAIN, table.getName()))
                            .as(select(asterisk()).from(name(schema, table.getName())))
                            .execute());
            tx.connection(c -> c.setSchema(MAIN));
            scripts.get(VIEWS).execute(tx);
            meta.getSchemas().stream()
                    .map(Named::getName)
                    .filter(old -> old.startsWith(SNAPSHOT) && !old.equals(schema))
                    .forEach(old -> tx.dropSchema(name(old)).cascade().execute());
        });
        published = true;
    }

    /** Drops this snapshot unless it was published, and gives the connection back to the pool, set to {@code main}. */
    @Override
    public void close() {
        try {
            db.connection(c -> c.setSchema(MAIN));
            if (!published) {
                db.dropSchemaIfExists(name(schema)).cascade().execute();
            }
        } finally {
            pool.release(connection);
        }
    }

    /** This snapshot's schema, made current, with the tables of {@code schema.sql}. */
    private void createSchema() {
        db.createSchema(name(schema)).execute();
        db.connection(c -> c.setSchema(schema));
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

    /** The source's row in {@code source}: its name and citation. */
    private InsertSetMoreStep<?> row(DSLContext tx, String source) {
        Source declared = sources.get(source);
        return tx.insertInto(SOURCE)
                .set(SOURCE.NAME, source)
                .set(SOURCE.TITLE, declared.title())
                .set(SOURCE.PAGE, declared.page().toString());
    }

    /** Each attempt's problem in order, e.g. {@code HTTP 503; needs faa-register}. */
    private static String problem(List<String> problems) {
        return String.join("; ", problems);
    }
}
