package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.format.FormatsTest;
import com.vector.airport.data.source.Downloader;
import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.SourceFile;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jooq.CloseableDSLContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileSystemUtils;

/**
 * Readers see one complete snapshot: a new one replaces it only when published, never half-built, and one that is never
 * published leaves nothing behind.
 */
class TablesTest {

    private static final ClassPathResource SQL = new ClassPathResource("snapshot-test/sql/");
    private static final List<String> SOURCES = List.of("list", "segments", "stats", "archive");
    private static final String PERIOD = "2026-01-01";

    @TempDir
    Path dir;

    /** One in-memory database per test, which both connections open. */
    private final String database = "jdbc:duckdb:memory:" + UUID.randomUUID();

    /** The connection a snapshot is built on. */
    @AutoClose
    private final CloseableDSLContext db = DSL.using(database);

    /** Another connection to the same database, as the app's readers have. */
    @AutoClose
    private final CloseableDSLContext reader = DSL.using(database);

    @Test
    void readersSeeANewSnapshotOnlyOnceItIsPublished() {
        publish("v1");

        try (Tables next = Tables.create(db, formats(), SQL, SOURCES)) {
            next.load("stats", version("v2"), true, List.of());

            assertThat(stats()).containsExactly("v1");
            next.publish();
        }

        assertThat(stats()).containsExactly("v2");
    }

    @Test
    void aSnapshotThatIsNeverPublishedChangesNothingAndIsDropped() {
        publish("v1");

        try (Tables next = Tables.create(db, formats(), SQL, SOURCES)) {
            next.load("stats", version("v2"), true, List.of());
        }

        assertThat(stats()).containsExactly("v1");
        assertThat(snapshots()).hasSize(1);
    }

    @Test
    void publishingDropsTheSnapshotBefore() {
        publish("v1");
        publish("v2");

        assertThat(snapshots()).hasSize(1);
    }

    @Test
    void readersQueryTheViewsAndMacrosOfViewsSql() {
        publish("v1");

        assertThat(reader.fetchValue("select stats from stats_count")).isEqualTo(1L);
        assertThat(reader.fetchValue("select shout('bos')")).isEqualTo("BOS");
    }

    @Test
    void aPublishThatFailsLeavesTheSnapshotBeforeServing() {
        publish("v1");

        try (Tables next =
                Tables.create(db, formats(), folder("views.sql", "create view broken as select nope;"), SOURCES)) {
            next.load("stats", version("v2"), true, List.of());

            assertThatThrownBy(next::publish).hasMessageContaining("nope");
        }

        assertThat(stats()).containsExactly("v1");
        assertThat(snapshots()).hasSize(1);
    }

    @Test
    void aSchemaThatFailsLeavesNoSnapshotBehind() {
        assertThatThrownBy(() -> Tables.create(db, formats(), folder("schema.sql", "create table broken (;"), SOURCES))
                .hasMessageContaining("broken");

        assertThat(snapshots()).isEmpty();
    }

    @Test
    void sourceThatFailsLeavesNoneOfItsRowsBehind() {
        try (Tables tables = Tables.create(db, formats(), SQL, SOURCES)) {
            assertThatThrownBy(() -> tables.load("stats", version("v1", "wrong"), true, List.of()))
                    .hasMessageContaining("file holds the wrong value");
            tables.publish();
        }

        assertThat(stats()).isEmpty();
    }

    @Test
    void sourceThatWritesNoRowsFails() {
        try (Tables tables = Tables.create(db, formats(), SQL, SOURCES)) {
            assertThatThrownBy(() -> tables.load("list", list(), true, List.of()))
                    .hasMessage(PERIOD + " wrote no rows");
        }
    }

    @Test
    void aLoadedSourceKeepsWhyTheAttemptsBeforeFailed() {
        try (Tables tables = Tables.create(db, formats(), SQL, SOURCES)) {
            tables.load("stats", version("v1"), true, List.of("2027: HTTP 503"));
            tables.load("list", list("BOS"), true, List.of());
            tables.publish();
        }

        assertThat(problem("stats")).isEqualTo("2027: HTTP 503");
        assertThat(problem("list")).isNull();
    }

    @Test
    void aSourceLeftOutRecordsEveryAttemptsProblem() {
        try (Tables tables = Tables.create(db, formats(), SQL, SOURCES)) {
            tables.leaveOut("stats", List.of("2027: HTTP 503", "2026: file holds the wrong value"));
            tables.publish();
        }

        assertThat(problem("stats")).isEqualTo("2027: HTTP 503; 2026: file holds the wrong value");
    }

    @Test
    void aZipEntryPlaceholderReadsThatEntry() {
        try (Tables tables = Tables.create(db, formats(), SQL, SOURCES)) {
            tables.load("archive", new Version(PERIOD, List.of(zip("stats.csv", "value\nzipped\n"))), true, List.of());
            tables.publish();
        }

        assertThat(stats()).containsExactly("zipped");
    }

    @Test
    void anUnknownPlaceholderFailsNamingItsFile() {
        Resource typo = folder("typo.sql", "insert into stats select value from read_csv({flie}, all_varchar = true);");

        try (Tables tables = Tables.create(db, formats(), typo, List.of("typo"))) {
            assertThatThrownBy(() -> tables.load("typo", version("v1"), true, List.of()))
                    .hasMessage("Unknown placeholder {flie} in typo.sql; there is {file} and {file:ENTRY}");
        }
    }

    @Test
    void pooledConnectionOutlivesAFailedLoad() {
        try (HikariDataSource pool = new HikariDataSource();
                Tables tables = Tables.create(pooled(pool), formats(), SQL, SOURCES)) {
            assertThatThrownBy(() -> tables.load("list", list("BOS", "BOS"), true, List.of()))
                    .hasMessageContaining("PRIMARY KEY or UNIQUE constraint violation");

            tables.load("stats", version("v1"), true, List.of());
        }
    }

    @Test
    void sourceWithoutItsSqlFileFailsAtStartup() {
        assertThatThrownBy(() -> Tables.create(db, formats(), SQL, List.of("nosql")))
                .hasMessageContaining("Missing SQL file nosql.sql");
    }

    private void publish(String value) {
        try (Tables tables = Tables.create(db, formats(), SQL, SOURCES)) {
            tables.load("stats", version(value), true, List.of());
            tables.publish();
        }
    }

    /** The stats source's files, one per value, oldest first. */
    private Version version(String... values) {
        return new Version(
                PERIOD,
                Arrays.stream(values).map(value -> csv("stats", "value", value)).toList());
    }

    /** The list source's file holding {@code codes}. */
    private Version list(String... codes) {
        return new Version(PERIOD, List.of(csv("list", "code", codes)));
    }

    /** The source's CSV file: a header, then one row per value. */
    private Fetched csv(String source, String header, String... rows) {
        try {
            Path file = Files.write(
                    Files.createTempFile(dir, source, ".csv"),
                    Stream.concat(Stream.of(header), Arrays.stream(rows)).toList());
            return fetched(source, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A zip holding one entry. */
    private Fetched zip(String entry, String content) {
        try {
            Path file = Files.createTempFile(dir, "archive", ".zip");
            try (OutputStream out = Files.newOutputStream(file);
                    ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return fetched("archive", file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Fetched fetched(String source, Path file) {
        SourceFile published = new SourceFile(Downloader.STORED, "https://example.org/%s".formatted(source), Form.NONE);
        return new Fetched(published, file, file.getFileName().toString());
    }

    /** The test's SQL folder with {@code file} holding {@code sql}. */
    private Resource folder(String file, String sql) {
        try {
            Path folder = dir.resolve("sql");
            FileSystemUtils.copyRecursively(SQL.getFile().toPath(), folder);
            Files.writeString(folder.resolve(file), sql + "\n");
            return new FileSystemResource(folder + "/");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Connections from Hikari, as the app's: it closes one it takes for lost. */
    private static DSLContext pooled(HikariDataSource pool) {
        pool.setJdbcUrl("jdbc:duckdb:memory:" + UUID.randomUUID());
        return DSL.using(pool, SQLDialect.DUCKDB);
    }

    private List<String> stats() {
        return reader.fetch("select value from stats").getValues(0, String.class);
    }

    private String problem(String source) {
        return reader.fetchSingle("select problem from source where name = ?", source)
                .get(0, String.class);
    }

    private List<String> snapshots() {
        return reader.fetch("select schema_name from information_schema.schemata where schema_name like 'snapshot_%'")
                .getValues(0, String.class);
    }

    /** Every format, as Spring builds them. */
    static Formats formats() {
        return FormatsTest.formats();
    }
}
