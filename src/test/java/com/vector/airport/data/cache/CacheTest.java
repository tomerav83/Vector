package com.vector.airport.data.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.downloader.Downloader;
import com.vector.airport.data.downloader.Downloaders;
import com.vector.airport.data.format.FormatsTest;
import com.vector.airport.data.lock.LockedFile;
import com.vector.airport.data.lock.LockedSource;
import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.Livecheck;
import com.vector.airport.data.source.Source;
import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.web.Unpublished;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A locked file is downloaded once and kept only when its source loaded, the newest is downloaded again, and a download
 * without rows, or a web page where a table should be, is not published yet; a frozen cache never downloads.
 */
class CacheTest {

    private static final URI PAGE = URI.create("https://example.org/");

    private static final Source STATS = new Source(
            "Stats", PAGE, "https://example.org/stats.csv", SourceFile.STORED, Form.NONE, null, 0, SourceFile.TABLE);
    private static final SourceFile FILE = STATS.file(Source.CURRENT);
    private static final LockedSource LOCKED =
            new LockedSource(Source.CURRENT, List.of(new LockedFile(FILE, "fingerprint")));
    private static final Source YEARLY = new Source(
            "Stats",
            PAGE,
            "https://example.org/stats-{year}.csv",
            SourceFile.STORED,
            Form.NONE,
            new Livecheck("https://example.org/", Pattern.compile("stats-(\\d{4})"), "yyyy"),
            1,
            SourceFile.TABLE);

    @TempDir
    Path dir;

    private final List<String> downloads = new ArrayList<>();
    private String served = "value\nv1\n";

    @Test
    void keptFileIsNotDownloadedAgain() {
        try (Cache cache = cache()) {
            cache.keep(cache.fetch(LOCKED));
            cache.fetch(LOCKED);
        }

        assertThat(downloads).containsExactly(FILE.url());
    }

    @Test
    void newestIsDownloadedAgainThoughItIsCached() {
        try (Cache cache = cache()) {
            cache.keep(cache.fetch(LOCKED));
            cache.download(STATS, List.of(Source.CURRENT));
        }

        assertThat(downloads).containsExactly(FILE.url(), FILE.url());
    }

    @Test
    void newestPeriodsAreOneFileEachOldestFirst() {
        try (Cache cache = cache()) {
            assertThat(cache.download(YEARLY, List.of("2025-01-01", "2026-01-01"))
                            .period())
                    .isEqualTo("2026-01-01");
        }

        assertThat(downloads)
                .containsExactly("https://example.org/stats-2025.csv", "https://example.org/stats-2026.csv");
    }

    @Test
    void downloadWithoutRowsIsNotPublishedYet() {
        served = "value\n";

        try (Cache cache = cache()) {
            assertThatThrownBy(() -> cache.fetch(LOCKED))
                    .isInstanceOf(Unpublished.class)
                    .hasMessage("https://example.org/stats.csv holds no data rows");
        }
    }

    @Test
    void webPageIsNotPublishedYetUnlessItIsTheFileItself() {
        served = "<p>In the U.S., the Level 3 airports are JFK, LGA and DCA.</p>";
        SourceFile page = new SourceFile(SourceFile.STORED, FILE.url(), Form.NONE, SourceFile.PAGE);

        try (Cache cache = cache()) {
            assertThatThrownBy(() -> cache.fetch(LOCKED)).isInstanceOf(Unpublished.class);
            assertThat(cache.fetch(new LockedSource(Source.CURRENT, List.of(new LockedFile(page, "fingerprint"))))
                            .files())
                    .hasSize(1);
        }
    }

    @Test
    void downloadThatCannotBeKeptIsDownloadedAgainNextTime() throws IOException {
        try (Cache cache = cache()) {
            Version version = cache.fetch(LOCKED);
            Files.delete(version.files().getFirst().path());

            cache.keep(version);
            cache.fetch(LOCKED);
        }

        assertThat(downloads).containsExactly(FILE.url(), FILE.url());
    }

    @Test
    void frozenCacheFailsOnALockedFileItLacksInsteadOfDownloading() {
        try (Cache cache = cache(true)) {
            assertThatThrownBy(() -> cache.fetch(LOCKED))
                    .hasMessage(FILE.key() + " is not in " + dir + ", and vector.data.frozen never downloads");
        }

        assertThat(downloads).isEmpty();
    }

    @Test
    void closingDeletesTheDownloadsThatWereNotKept() {
        try (Cache cache = cache()) {
            cache.fetch(LOCKED);
        }

        assertThat(dir).isEmptyDirectory();
    }

    /** A cache whose only downloader notes each url and saves {@link #served}. */
    private Cache cache() {
        return cache(false);
    }

    private Cache cache(boolean frozen) {
        Downloader site = (file, target) -> {
            downloads.add(file.url());
            Files.writeString(target, served);
        };
        return new Cache(dir, new Downloaders(Map.of(SourceFile.STORED, site)), FormatsTest.formats(), frozen);
    }
}
