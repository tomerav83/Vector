package com.vector.airport.data.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpCookieHandling;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP to the public data sites: Spring's {@link RestClient} with Spring Boot's settings
 * ({@code spring.http.clients.*}) and a session cookie; a call that fails with an I/O error or a 5xx, e.g. a BTS
 * timeout, is made again, body included ({@code vector.data.retries} times, the first after
 * {@code vector.data.backoff}, each wait twice the one before), a 404 is {@link Unpublished}, and any other error
 * status fails at once.
 */
@Component
@Retryable(
        includes = IOException.class,
        maxRetriesString = "${vector.data.retries}",
        delayString = "${vector.data.backoff}",
        multiplier = 2)
public class Web {

    private final RestClient http;

    public Web(RestClient.Builder builder, ClientHttpRequestFactoryBuilder<?> factory, HttpClientSettings settings) {
        this.http = builder
                // ASP.NET ties a download form's hidden state to the session cookie (BTS TranStats)
                .requestFactory(factory.build(settings.withCookieHandling(HttpCookieHandling.ENABLE)))
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (vector airport data loader)")
                .build();
    }

    /** Saves the file at {@code url} to {@code target}. */
    public void download(String url, Path target) {
        http.get().uri(URI.create(url)).exchange((request, response) -> save(request, response, target));
    }

    /** The page at {@code url}, e.g. a download form; its charset comes from its BOM or {@code <meta>}. */
    public Document page(String url) {
        return http.get()
                .uri(URI.create(url))
                .exchange((request, response) -> Jsoup.parse(body(request, response), null, url));
    }

    /**
     * The text at {@code url} as sent, e.g. FAA's {@code <editionDate>}, empty for an empty page; its charset comes
     * from {@code Content-Type}.
     */
    String read(String url) {
        return http.get().uri(URI.create(url)).exchange((request, response) -> {
            validate(request, response);
            return Objects.requireNonNullElse(response.bodyTo(String.class), "");
        });
    }

    /** Posts {@code form} ({@code application/x-www-form-urlencoded}) and saves the answer to {@code target}. */
    public void submit(String url, MultiValueMap<String, String> form, Path target) {
        http.post()
                .uri(URI.create(url))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange((request, response) -> save(request, response, target));
    }

    private static long save(HttpRequest request, ClientHttpResponse response, Path target) throws IOException {
        try (InputStream in = body(request, response)) {
            return Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream body(HttpRequest request, ClientHttpResponse response) throws IOException {
        validate(request, response);
        return response.getBody();
    }

    /**
     * Fails an error status by whether a retry can help.
     *
     * @throws Unpublished for a 404: the site has not published the file (yet)
     * @throws IOException for a 5xx: the site failed, so the call is made again
     * @throws IllegalStateException for any other error status, e.g. a 403: the site refuses, so a retry cannot help
     */
    private static void validate(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String failure = "HTTP %d from %s".formatted(status.value(), request.getURI());
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            throw new Unpublished(failure);
        }
        if (status.is5xxServerError()) {
            throw new IOException(failure);
        }
        if (status.isError()) {
            throw new IllegalStateException(failure);
        }
    }
}
