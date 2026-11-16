package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.web.FakeSite;
import com.vector.airport.data.web.Web;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;

/**
 * It loads its newest releases, each one file with the placeholders filled from its day; without a livecheck, only its
 * current state. Spring Boot binds it once from the profile, and a typo in a placeholder fails the start.
 */
class SourceTest {

    @AutoClose
    private final FakeSite site = new FakeSite();

    private final Web web =
            new Web(RestClient.builder(), ClientHttpRequestFactoryBuilder.jdk(), HttpClientSettings.defaults());

    private final ApplicationContextRunner app =
            new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

    /** The default profile, the US, as Spring Boot binds it. */
    private final ApplicationContextRunner us =
            new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void historyIsTheReleasesBeforeTheNewestOldestFirst() {
        assertThat(source("https://example.org/stats-{year}.csv", yearly(), 1).periods(web))
                .containsExactly("2025-01-01", "2026-01-01");
    }

    @Test
    void placeholdersAreTheValuesOfThePeriodsDay() {
        Source source =
                source("https://example.org/{date}/{year}/Q{quarter}/{from:yyyyMM}-{to:yyyyMM}.csv", yearly(), 0);

        assertThat(source.file("2026-07-01").url())
                .isEqualTo("https://example.org/2026-07-01/2026/Q3/202601-202612.csv");
    }

    @Test
    void sourceWithoutLivecheckHasOnlyItsCurrentState() {
        Source source = source("https://example.org/stats.csv", null, 0);

        assertThat(source.periods(web)).containsExactly(Source.CURRENT);
        assertThat(source.file(Source.CURRENT).url()).isEqualTo("https://example.org/stats.csv");
    }

    @Test
    void usFilesAreFilledFromTheirDay() {
        app.run(context -> assertThat(
                        source(context, "faa-register").file("2026-10-29").url())
                .isEqualTo("https://nfdc.faa.gov/webContent/28DaySub/extra/29_Oct_2026_APT_CSV.zip"));
    }

    @Test
    void eachPeriodPostsItsOwnFormFieldsInTheirYamlOrder() {
        app.run(context -> assertThat(source(context, "bts-segments")
                        .file("2025-01-01")
                        .form()
                        .fields())
                .containsExactly(
                        Map.entry("cboGeography", "All"),
                        Map.entry("cboYear", "2025"),
                        Map.entry("cboPeriod", "All"),
                        Map.entry("btnDownload", "Download")));
    }

    @Test
    void unsetKeysTakeTheirDefaults() {
        app.run(context -> assertThat(source(context, "faa-boardings").file("2025-01-01"))
                .extracting(SourceFile::downloader, SourceFile::form)
                .containsExactly(SourceFile.STORED, Form.NONE));
    }

    @Test
    void unknownPlaceholderFailsTheStartNamingTheSource() {
        app.withPropertyValues(
                        "spring.config.location=classpath:data-properties-test/", "spring.profiles.active=placeholder")
                .run(context -> assertThatThrownBy(() -> source(context, "stats"))
                        .hasMessageContaining("vector.data.sources.stats")
                        .rootCause()
                        .hasMessage(
                                "Unknown placeholder {yaer} in 'https://example.org/stats-{yaer}.csv'; available: []"));
    }

    /** The yearly releases the local site's release page names: 2024 to 2026. */
    private Livecheck yearly() {
        site.files.put(site.url("/releases"), "stats-2024 stats-2025 stats-2026");
        return new Livecheck(site.url("/releases"), Pattern.compile("stats-(\\d{4})"), "yyyy");
    }

    private static Source source(String url, Livecheck livecheck, int history) {
        return new Source(
                "Stats",
                URI.create("https://example.org/"),
                url,
                SourceFile.STORED,
                Form.NONE,
                livecheck,
                history,
                SourceFile.TABLE);
    }

    /** The source {@code name} as Spring Boot binds it from the active profiles, e.g. application-us.yml. */
    private static Source source(ApplicationContext context, String name) {
        return Binder.get(context.getEnvironment())
                .bind("vector.data.sources." + name, Source.class)
                .get();
    }
}
