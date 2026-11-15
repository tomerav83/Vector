package com.vector.airport.data.source.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.source.Unpublished;
import com.vector.airport.data.source.Web;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/**
 * The download button's post: the page's state fields (any attribute order) plus the choices, each of which the page
 * must still have; a page that changed fails instead of sending the site's default year.
 */
class OnDemandTest {

    private static final int ANY_FREE_PORT = 0;
    private static final Form FORM = new Form(Map.of("cboYear", "2025"), List.of("YEAR"));

    private static final String PAGE = """
            <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="abc=" />
            <input value="xyz" name="__EVENTVALIDATION" type="hidden">
            <INPUT type=hidden name='__EVENTARGUMENT' value='a&amp;b'>
            <input type="text" name="txtSearch" value="ignored" />
            <select name="cboYear"><option value="2024">2024</option><option selected value="2025">2025</option></select>
            <input type="checkbox" name="YEAR" />
            """;

    @TempDir
    Path dir;

    @Test
    void postsTheStateFieldsWithTheChoices() throws IOException {
        assertThat(OnDemand.body(FORM, Jsoup.parse(PAGE)).toSingleValueMap())
                .containsExactly(
                        entry("__VIEWSTATE", "abc="),
                        entry("__EVENTVALIDATION", "xyz"),
                        entry("__EVENTARGUMENT", "a&b"),
                        entry("cboYear", "2025"),
                        entry("YEAR", "on"));
    }

    @Test
    void aYearThePageDoesNotOfferIsNotPublished() {
        // BTS would ignore it and send its default year instead
        assertThatThrownBy(() -> OnDemand.body(new Form(Map.of("cboYear", "2026"), List.of()), Jsoup.parse(PAGE)))
                .isInstanceOf(Unpublished.class)
                .hasMessageContaining("cboYear=2026");
    }

    @Test
    void aFieldThePageLacksMeansThePageChanged() {
        // a renamed field would be ignored too, and every year would load the default year
        assertThatThrownBy(() -> OnDemand.body(new Form(Map.of("ddlYear", "2025"), List.of("YEAR")), Jsoup.parse(PAGE)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("has no [ddlYear]");
    }

    @Test
    void aBoxThePageLacksMeansThePageChanged() {
        assertThatThrownBy(() -> OnDemand.body(new Form(Map.of(), List.of("YEAR", "MONTH")), Jsoup.parse(PAGE)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("has no [MONTH]");
    }

    @Test
    void pageWithoutStateFieldsIsNotTheFormAnyMore() {
        assertThatThrownBy(() -> OnDemand.body(FORM, Jsoup.parse("<html>maintenance</html>")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no ASP.NET state fields");
    }

    @Test
    void repeatedStateFieldKeepsItsLastValue() throws IOException {
        String page = """
                <input name="__VIEWSTATE" value="old"><input name="__VIEWSTATE" value="new"><input name="YEAR">""";

        assertThat(OnDemand.body(new Form(Map.of(), List.of("YEAR")), Jsoup.parse(page))
                        .toSingleValueMap())
                .containsExactly(entry("__VIEWSTATE", "new"), entry("YEAR", "on"));
    }

    @Test
    void downloadsWhatThePostAnswers() throws IOException {
        HttpServer site = HttpServer.create(new InetSocketAddress("localhost", ANY_FREE_PORT), 0);
        site.createContext(
                "/DL.aspx",
                exchange -> reply(exchange, exchange.getRequestMethod().equals("POST") ? "data" : PAGE));
        site.start();
        try {
            String url =
                    "http://localhost:%d/DL.aspx".formatted(site.getAddress().getPort());

            new OnDemand(web()).download(new SourceFile("on-demand", url, FORM), dir.resolve("file"));

            assertThat(dir.resolve("file")).content().isEqualTo("data");
        } finally {
            site.stop(0);
        }
    }

    private static Web web() {
        return new Web(RestClient.builder(), ClientHttpRequestFactoryBuilder.jdk(), HttpClientSettings.defaults());
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HttpStatus.OK.value(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
