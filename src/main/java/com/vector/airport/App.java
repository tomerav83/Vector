package com.vector.airport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Airport Investment Intelligence Agent: ranks and compares US airports for terminal-expansion investment.
 *
 * <p>{@code data} builds the airport tables from the public sources before the app serves; the agent reads them through
 * jOOQ.
 */
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
