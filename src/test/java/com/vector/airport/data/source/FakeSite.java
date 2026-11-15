package com.vector.airport.data.source;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * A web site on localhost for every HTTP test, which opens a session with a cookie as ASP.NET does: a file in
 * {@code files} serves its text, one in {@code down} answers 503, one in {@code flaky} answers 503 once, one in
 * {@code forbidden} answers 403, the index ({@code /}) lists every file, and any other is a 404. Like an ASP.NET form
 * page (BTS TranStats), it refuses a post without its session cookie.
 */
public class FakeSite implements AutoCloseable {

    /** Where snapshot-test/sources.yml publishes its sources; the test points it at {@link #url}. */
    public static final String ADDRESS = "https://example.org";

    /** The cookie every answer sets, as ASP.NET ties a form's hidden state to its session. */
    public static final String SESSION = "ASP.NET_SessionId=s1";

    private static final int ANY_FREE_PORT = 0;

    public final Map<String, String> files = new ConcurrentHashMap<>();
    public final Set<String> down = ConcurrentHashMap.newKeySet();
    public final Set<String> flaky = ConcurrentHashMap.newKeySet();
    public final Set<String> forbidden = ConcurrentHashMap.newKeySet();

    /** The url of each request, in order. */
    public final List<String> asked = new CopyOnWriteArrayList<>();

    /** The cookie each request came with, empty for none, in order. */
    public final List<String> cookies = new CopyOnWriteArrayList<>();

    /** The body of each post, e.g. {@code cboYear=2025&YEAR=on}. */
    public final List<String> posted = new CopyOnWriteArrayList<>();

    private final HttpServer server;

    public FakeSite() {
        server = listen(this::answer);
    }

    /** The address of {@code path} on this site, e.g. {@code http://localhost:4711/stats-2026.csv}. */
    public String url(String path) {
        return "http://localhost:%d%s".formatted(server.getAddress().getPort(), path);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String url = url(exchange.getRequestURI().getPath());
        asked.add(url);
        cookies.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst(HttpHeaders.COOKIE), ""));
        if (isPost(exchange)) {
            posted.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
        exchange.getResponseHeaders().add(HttpHeaders.SET_COOKIE, SESSION + "; path=/");
        if (down.contains(url) || flaky.remove(url)) {
            reply(exchange, HttpStatus.SERVICE_UNAVAILABLE, "down");
        } else if (forbidden.contains(url)
                || isPost(exchange) && !cookies.getLast().contains(SESSION)) {
            reply(exchange, HttpStatus.FORBIDDEN, "no robots");
        } else if (url.equals(url("/"))) {
            reply(exchange, HttpStatus.OK, String.join("\n", files.keySet()));
        } else {
            Optional<String> file = Optional.ofNullable(files.get(url));
            reply(exchange, file.isPresent() ? HttpStatus.OK : HttpStatus.NOT_FOUND, file.orElse("no such file"));
        }
    }

    private static boolean isPost(HttpExchange exchange) {
        return HttpMethod.POST.matches(exchange.getRequestMethod());
    }

    private static HttpServer listen(HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", ANY_FREE_PORT), 0);
            server.createContext("/", handler);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void reply(HttpExchange exchange, HttpStatus status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status.value(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
