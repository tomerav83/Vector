package com.vector.airport.data.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.resilience.annotation.RetryAnnotationBeanPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * A local web site: status codes mean what they say, a 5xx or an I/O error is tried again and nothing else is, and a
 * form is posted back in the page's session.
 */
class WebTest {

    private static final int RETRIES = 2;
    private static final String RELEASE = "<edition><editionDate>10/29/2026</editionDate></edition>";

    @TempDir
    Path dir;

    @AutoClose
    private final FakeSite site = new FakeSite();

    private final String file = site.url("/file.csv");

    /** As the app makes it, its {@code @Retryable} on. */
    private final ApplicationContextRunner app = new ApplicationContextRunner()
            .withBean(RetryAnnotationBeanPostProcessor.class)
            .withBean(Web.class, WebTest::web)
            .withPropertyValues("vector.data.retries=" + RETRIES, "vector.data.backoff=1ms");

    @Test
    void savesTheBodyOfAnOk() {
        site.files.put(file, "data");

        web().download(file, dir.resolve("file"));

        assertThat(dir.resolve("file")).content().isEqualTo("data");
    }

    @Test
    void notFoundMeansNotPublishedAndIsNotTriedAgain() {
        app.run(context -> assertThatThrownBy(() -> context.getBean(Web.class).download(file, dir.resolve("file")))
                .isInstanceOf(Unpublished.class)
                .hasMessage("HTTP 404 from " + file));

        assertThat(site.asked).containsExactly(file);
    }

    @Test
    void serverErrorIsTriedAgain() {
        site.files.put(file, "data");
        site.flaky.add(file);

        app.run(context -> context.getBean(Web.class).download(file, dir.resolve("file")));

        assertThat(site.asked).containsExactly(file, file);
        assertThat(dir.resolve("file")).content().isEqualTo("data");
    }

    @Test
    void givesUpAfterItsRetriesWithTheLastError() {
        site.down.add(file);

        app.run(context -> assertThatThrownBy(() -> context.getBean(Web.class).download(file, dir.resolve("file")))
                .hasRootCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("HTTP 503 from " + file));

        assertThat(site.asked).hasSize(RETRIES + 1);
    }

    @Test
    void refusalIsNotTriedAgain() {
        site.forbidden.add(file);

        app.run(context -> assertThatThrownBy(() -> context.getBean(Web.class).download(file, dir.resolve("file")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HTTP 403 from " + file));

        assertThat(site.asked).containsExactly(file);
    }

    @Test
    void textIsReadAsSentKeepingTheCaseOfXmlTags() {
        site.files.put(file, RELEASE);

        assertThat(web().read(file)).isEqualTo(RELEASE);
    }

    @Test
    void emptyPageIsEmptyText() {
        site.files.put(file, "");

        assertThat(web().read(file)).isEmpty();
    }

    @Test
    void formIsPostedBackInThePagesSession() {
        site.files.put(file, "data");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.set("cboPeriod", "All Years+");
        form.set("YEAR", "on");
        Web web = web();

        web.page(file);
        web.submit(file, form, dir.resolve("file"));

        assertThat(site.posted).containsExactly("cboPeriod=All+Years%2B&YEAR=on");
        assertThat(site.cookies).as("the session cookie the page set").containsExactly("", FakeSite.SESSION);
        assertThat(dir.resolve("file")).content().isEqualTo("data");
    }

    @Test
    void postOutsideThePagesSessionFails() {
        site.files.put(file, "data");

        assertThatThrownBy(() -> web().submit(file, new LinkedMultiValueMap<>(), dir.resolve("file")))
                .hasMessage("HTTP 403 from " + file);
    }

    private static Web web() {
        return new Web(RestClient.builder(), ClientHttpRequestFactoryBuilder.jdk(), HttpClientSettings.defaults());
    }
}
