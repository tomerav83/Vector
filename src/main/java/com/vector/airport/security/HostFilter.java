package com.vector.airport.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers 403 to a request another site's page sent, e.g. a script posting to the chat, or one for a host that is not
 * this machine, as after DNS rebinding. Curl, MCP clients and the chat page pass; the MCP spec asks for this check
 * ("Security Warning" of its Streamable HTTP transport). The actuator's port is a server of its own, which it does not
 * reach.
 */
@Component
class HostFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL = Set.of("localhost", "127.0.0.1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean local = LOCAL.contains(request.getServerName())
                && Optional.ofNullable(request.getHeader(HttpHeaders.ORIGIN))
                        .map(origin -> LOCAL.stream().anyMatch(host -> origin.startsWith("http://" + host + ":")))
                        .orElse(true);
        if (local) {
            chain.doFilter(request, response);
        } else {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN, "Vector answers only pages and clients on this machine");
        }
    }
}
