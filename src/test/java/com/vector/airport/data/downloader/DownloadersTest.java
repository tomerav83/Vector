package com.vector.airport.data.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.Source;
import com.vector.airport.data.source.SourceFile;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A source names its downloader by bean name; a name that is not a bean fails the start. */
class DownloadersTest {

    private final Downloader stored = (file, target) -> {};
    private final Downloaders downloaders = new Downloaders(Map.of(SourceFile.STORED, stored));

    @Test
    void findsTheBeanByName() {
        assertThat(downloaders.forName(SourceFile.STORED, "stats")).isSameAs(stored);
    }

    @Test
    void unknownDownloaderFailsNamingTheSourceAndTheChoices() {
        Source source = new Source(
                "Stats",
                URI.create("https://example.org/"),
                "https://example.org/stats.csv",
                "on-demnd",
                Form.NONE,
                null,
                0,
                SourceFile.TABLE);

        assertThatThrownBy(() -> downloaders.validate(Map.of("stats", source)))
                .hasMessageContaining("stats: unknown downloader 'on-demnd'; there are [stored]");
    }
}
