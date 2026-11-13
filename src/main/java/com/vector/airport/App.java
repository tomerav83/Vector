package com.vector.airport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Airport Investment Intelligence Agent.
 *
 * <p>Package layout (ports and adapters):
 * <ul>
 *   <li>{@code core} – scoring, KPIs, tools; depends only on {@code ports}, jOOQ and Spring AI abstractions.</li>
 *   <li>{@code ports} – interfaces for every external boundary (country data, raw cache, live APIs).</li>
 *   <li>{@code adapters} – concrete implementations (US data, local file cache, FAA/DOT live APIs).</li>
 * </ul>
 * The boundary is enforced by {@code ArchitectureTest}.
 */
@SpringBootApplication
public class App {
	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}
}