package com.vector.airport.data;

import static com.vector.airport.view.tables.Source.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vector.airport.data.downloader.Downloaders;
import com.vector.airport.data.format.FormatsTest;
import com.vector.airport.data.lock.Lock;
import com.vector.airport.data.lock.LockedFile;
import com.vector.airport.data.lock.LockedSource;
import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.Source;
import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.web.FakeSite;
import com.vector.airport.data.web.Web;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestClient;

/**
 * A start loads the locked files (cache, else source), a refresh moves to the newest, no single source stops the app or
 * replaces good data with bad, each problem is named, the lock changes only when an entry does, and every build is
 * announced.
 */
class SnapshotTest {

    private static final String THIS_YEAR = "2026-01-01";
    private static final String LAST_YEAR = "2025-01-01";
    private static final String TWO_YEARS_AGO = "2024-01-01";

    @TempDir
    Path dir;

    @AutoClose
    private final FakeSite site = new FakeSite();

    @AutoClose
    private final CloseableDSLContext db = DSL.using("jdbc:duckdb:");

    private final List<SnapshotPublished> published = new ArrayList<>();

    @Test
    void firstStartTakesTheNewestPeriodAndLocksIt() throws IOException {
        publish("stats", LAST_YEAR, "v1");

        start();

        assertThat(lock().find("stats")).contains(locked(LAST_YEAR, "v1"));
        assertThat(source("stats")).containsExactly(LAST_YEAR, true, null);
    }

    @Test
    void startKeepsTheLockedPeriodWhenANewerOneIsOut() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        publish("stats", THIS_YEAR, "v2");

        start();

        assertThat(lock().find("stats").map(LockedSource::period)).contains(LAST_YEAR);
        assertThat(stats()).containsExactly("v1");
    }

    @Test
    void startReadsTheCacheInsteadOfDownloading() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        site.asked.clear();

        start();

        assertThat(site.asked).doesNotContain(url("stats", LAST_YEAR));
    }

    @Test
    void startDownloadsTheLockedFileAsTheLockNamesIt() throws IOException {
        SourceFile moved = new SourceFile(SourceFile.STORED, site.url("/moved.csv"), Form.NONE, SourceFile.TABLE);
        new Lock(Map.of("stats", new LockedSource(LAST_YEAR, List.of(new LockedFile(moved, fingerprint("v1"))))))
                .write(dir.resolve("sources.lock.json"));
        site.files.put(moved.url(), csv("v1"));

        start();

        assertThat(stats()).containsExactly("v1");
        assertThat(source("stats")).containsExactly(LAST_YEAR, true, null);
    }

    @Test
    void frozenStartFailsNamingTheSourcesTheLockLacksBeforeDownloading() throws IOException {
        publish("stats", LAST_YEAR, "v1");

        assertThatThrownBy(() -> snapshot(false, true).afterSingletonsInstantiated())
                .hasMessageContaining("has no entry for [list, segments, stats]");
        assertThat(site.asked).isEmpty();
    }

    @Test
    void refreshMovesToTheNewestPeriodAndRewritesTheLock() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        publish("stats", THIS_YEAR, "v2");

        refresh();

        assertThat(lock().find("stats")).contains(locked(THIS_YEAR, "v2"));
        assertThat(stats()).containsExactly("v2");
    }

    @Test
    void refreshFlagMakesTheStartARefresh() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        publish("stats", THIS_YEAR, "v2");

        snapshot(true).afterSingletonsInstantiated();

        assertThat(lock().find("stats")).contains(locked(THIS_YEAR, "v2"));
    }

    @Test
    void refreshThroughTheEndpointAnswersWithTheNewSourceTable() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        Snapshot snapshot = snapshot(false);
        snapshot.afterSingletonsInstantiated();
        publish("stats", THIS_YEAR, "v2");

        assertThat(new DataEndpoint(snapshot, db).refresh())
                .filteredOn(row -> row.get("name").equals("stats"))
                .extracting(row -> row.get("period"))
                .containsExactly(THIS_YEAR);
        assertThat(stats()).containsExactly("v2");
    }

    @Test
    void newestIsTheNewestReleaseTheLivecheckPageNames() throws IOException {
        publish("stats", TWO_YEARS_AGO, "v0");
        publish("stats", LAST_YEAR, "v1");

        start();

        assertThat(lock().find("stats").map(LockedSource::period)).contains(LAST_YEAR);
    }

    @Test
    void refreshKeepsTheLockedPeriodWhenTheLivecheckPageNamesNoRelease() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        site.files.clear(); // the page's layout changed: the regex finds nothing

        refresh();

        assertThat(stats()).containsExactly("v1");
        assertThat(lock().find("stats").map(LockedSource::period)).contains(LAST_YEAR);
    }

    @Test
    void upstreamRevisionIsLoadedAndFlaggedButNotLocked() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        FileSystemUtils.deleteRecursively(cache()); // a fresh clone: nothing cached
        publish("stats", LAST_YEAR, "v1 revised by the agency");

        start();

        assertThat(stats()).containsExactly("v1 revised by the agency");
        assertThat(source("stats")).containsExactly(LAST_YEAR, false, null);
        assertThat(lock().find("stats").orElseThrow().files())
                .extracting(LockedFile::fingerprint)
                .containsExactly(fingerprint("v1"));
    }

    @Test
    void sourceThatCannotBeDownloadedIsLeftOutWithWhatNeedsItAndTheAppStarts() throws IOException {
        site.down.add(url("list", Source.CURRENT));
        publish("segments", LAST_YEAR, "BOS");
        publish("stats", LAST_YEAR, "v1");

        start();

        assertThat(problem("list")).contains("HTTP 503");
        assertThat(problem("segments")).contains("needs list");
        assertThat(source("stats")).containsExactly(LAST_YEAR, true, null);
        assertThat(lock().find("list")).isEmpty();
    }

    @Test
    void refreshKeepsTheLockedPeriodOfASourceThatIsDownAndSaysWhy() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        publish("stats", THIS_YEAR, "v2");
        site.down.add(url("stats", THIS_YEAR));

        refresh();

        assertThat(stats()).containsExactly("v1");
        assertThat(lock().find("stats").map(LockedSource::period)).contains(LAST_YEAR);
        assertThat(problem("stats")).contains("HTTP 503");
    }

    @Test
    void refreshKeepsTheLockEntryOfASourceItLeavesOut() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        FileSystemUtils.deleteRecursively(cache());
        site.down.add(url("stats", LAST_YEAR));

        refresh();

        assertThat(problem("stats")).contains("HTTP 503");
        assertThat(lock().find("stats")).contains(locked(LAST_YEAR, "v1"));
    }

    @Test
    void refreshKeepsTheLockedVersionAndItsCachedFileWhenTheNewestFailsItsCheck() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        publish("stats", LAST_YEAR, "wrong"); // the same period, republished broken

        refresh();

        assertThat(stats()).containsExactly("v1");
        assertThat(lock().find("stats").orElseThrow().files())
                .extracting(LockedFile::fingerprint)
                .containsExactly(fingerprint("v1"));
        assertThat(cached()).containsExactly(csv("v1"));
    }

    @Test
    void refreshThatFailsKeepsServingTheCurrentData() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        Snapshot snapshot = snapshot(false);
        snapshot.afterSingletonsInstantiated();
        publish("stats", THIS_YEAR, "v2");
        Files.writeString(dir.resolve("sources.lock.json"), "{"); // a merge left the lock unreadable

        assertThatThrownBy(snapshot::refresh).hasMessageContaining("end-of-input");
        assertThat(stats()).containsExactly("v1");
    }

    @Test
    void lockThatCannotBeWrittenPublishesNothing() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        publish("stats", THIS_YEAR, "v2");
        Path lock = dir.resolve("sources.lock.json");
        Files.setPosixFilePermissions(lock, PosixFilePermissions.fromString("r--r--r--"));
        assumeFalse(Files.isWritable(lock), "root writes a read-only file");

        assertThatThrownBy(this::refresh).hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThat(stats()).containsExactly("v1");
        assertThat(published).hasSize(1);
    }

    @Test
    void problemOfASiteThatCannotBeReachedNamesTheException() throws IOException {
        SourceFile unreachable = new SourceFile(
                SourceFile.STORED,
                "http://localhost:%d/stats.csv".formatted(closedPort()),
                Form.NONE,
                SourceFile.TABLE);
        new Lock(Map.of("stats", new LockedSource(LAST_YEAR, List.of(new LockedFile(unreachable, fingerprint("v1"))))))
                .write(dir.resolve("sources.lock.json"));

        start();

        assertThat(problem("stats")).contains("Exception"); // the JDK's has no message: it read "null"
    }

    @Test
    void startThatChangesNothingLeavesTheLockAlone() throws IOException {
        publish("stats", LAST_YEAR, "v1");
        start();
        Files.setLastModifiedTime(dir.resolve("sources.lock.json"), FileTime.fromMillis(0));

        start();

        assertThat(Files.getLastModifiedTime(dir.resolve("sources.lock.json"))).isEqualTo(FileTime.fromMillis(0));
    }

    @Test
    void everyBuildIsAnnouncedWithWhatItLoaded() throws IOException {
        site.down.add(url("list", Source.CURRENT));
        publish("stats", LAST_YEAR, "v1");

        start();

        assertThat(published).containsExactly(new SnapshotPublished(Map.of("stats", LAST_YEAR)));
    }

    /** {@code value} as the source's file for {@code period}. */
    private void publish(String source, String period, String value) {
        site.files.put(url(source, period), csv(value));
    }

    private String url(String source, String period) {
        return site.url(
                period.equals(Source.CURRENT)
                        ? "/" + source + ".csv"
                        : "/%s-%d.csv".formatted(source, LocalDate.parse(period).getYear()));
    }

    private static String csv(String value) {
        return "value\n" + value + "\n";
    }

    /** A start, as the app's first build. */
    private void start() throws IOException {
        snapshot(false).afterSingletonsInstantiated();
    }

    /**
     * As Spring Boot binds it: snapshot-test/sources.yml on the fake site, plus where this test keeps its files.
     *
     * @param refresh {@code vector.data.refresh}: the start refreshes
     */
    private Snapshot snapshot(boolean refresh) throws IOException {
        return snapshot(refresh, false);
    }

    /** @param frozen {@code vector.data.frozen}: the start never downloads */
    private Snapshot snapshot(boolean refresh, boolean frozen) throws IOException {
        String yaml = new ClassPathResource("snapshot-test/sources.yml").getContentAsString(StandardCharsets.UTF_8);
        PropertySource<?> sources = new YamlPropertySourceLoader()
                .load(
                        "sources",
                        new ByteArrayResource(
                                yaml.replace(FakeSite.ADDRESS, site.url("")).getBytes(StandardCharsets.UTF_8)))
                .getFirst();
        PropertySource<?> here = new MapPropertySource(
                "here",
                Map.of(
                        "vector.data.cache-dir", cache().toString(),
                        "vector.data.lock", dir.resolve("sources.lock.json").toString(),
                        "vector.data.refresh", Boolean.toString(refresh),
                        "vector.data.frozen", Boolean.toString(frozen)));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(sources);
        environment.getPropertySources().addFirst(here);
        DataProperties properties = Binder.get(environment).bindOrCreate("vector.data", DataProperties.class);
        Web web = new Web(RestClient.builder(), ClientHttpRequestFactoryBuilder.jdk(), HttpClientSettings.defaults());
        return new Snapshot(
                properties,
                new Downloaders(Map.of(SourceFile.STORED, (file, target) -> web.download(file.url(), target))),
                FormatsTest.formats(),
                web,
                db,
                event -> published.add((SnapshotPublished) event));
    }

    private Path cache() {
        return dir.resolve("cache");
    }

    private Lock lock() {
        return Lock.read(dir.resolve("sources.lock.json"));
    }

    /** The lock entry of {@code stats} with its file of {@code period} holding {@code value}. */
    private LockedSource locked(String period, String value) {
        SourceFile file = new SourceFile(SourceFile.STORED, url("stats", period), Form.NONE, SourceFile.TABLE);
        return new LockedSource(period, List.of(new LockedFile(file, fingerprint(value))));
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(csv(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Its period, whether it matches the lock, and its problem. */
    private Object[] source(String name) {
        return db.fetchSingle(select(SOURCE.PERIOD, SOURCE.MATCHES_LOCK, SOURCE.PROBLEM)
                        .from(SOURCE)
                        .where(SOURCE.NAME.eq(name)))
                .intoArray();
    }

    private List<String> stats() {
        return db.fetchValues(select(field(name("value"), String.class)).from(name("stats")));
    }

    private void refresh() throws IOException {
        snapshot(false).refresh();
    }

    private String problem(String name) {
        return db.fetchSingle(SOURCE, SOURCE.NAME.eq(name)).getProblem();
    }

    /** The content of every file in the cache. */
    private List<String> cached() throws IOException {
        try (Stream<Path> files = Files.list(cache())) {
            return files.filter(Files::isRegularFile).map(SnapshotTest::content).toList();
        }
    }

    private static String content(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A port nothing listens on any more. */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
