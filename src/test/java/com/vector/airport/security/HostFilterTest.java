package com.vector.airport.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Who reaches the chat and the MCP server: a page or client on this machine, never another site's page. */
class HostFilterTest {

    @ParameterizedTest(name = "{3}")
    @CsvSource(delimiter = '|', textBlock = """
            localhost              |                                    | 200 | curl or an MCP client
            127.0.0.1              | http://127.0.0.1:8080              | 200 | the chat page
            evil.example           | http://evil.example:8080           | 403 | a page after DNS rebinding
            127.0.0.1              | https://evil.example               | 403 | another site's page
            127.0.0.1              | null                               | 403 | a sandboxed page
            localhost.evil.example | http://localhost.evil.example:8080 | 403 | a look-alike domain
            """)
    void answersOnlyThisMachine(String host, String origin, int status, String caller)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setServerName(host);
        Optional.ofNullable(origin).ifPresent(value -> request.addHeader(HttpHeaders.ORIGIN, value));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new HostFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).as(caller).isEqualTo(status);
    }
}
