package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A source names its downloader by bean name; a name that is not a bean fails the start. */
class DownloadersTest {

    private final Downloader stored = (file, target) -> {};
    private final Downloaders downloaders = new Downloaders(Map.of(Downloader.STORED, stored));

    @Test
    void findsTheBeanByName() {
        assertThat(downloaders.forName(Downloader.STORED, "stats")).isSameAs(stored);
    }

    @Test
    void unknownDownloaderFailsNamingTheSourceAndTheChoices() {
        Source source = new Source("https://example.org/stats.csv", "on-demnd", Form.NONE, null, 0);

        assertThatThrownBy(() -> downloaders.validate(Map.of("stats", source)))
                .hasMessageContaining("stats: unknown downloader 'on-demnd'; there are [stored]");
    }
}
