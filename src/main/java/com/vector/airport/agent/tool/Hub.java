package com.vector.airport.agent.tool;

/**
 * The FAA hub size of a primary airport (10,000+ boardings a year) by its share of US boardings, as the {@code airport}
 * table's {@code hub} names it, e.g. LARGE for the 30 busiest.
 */
public enum Hub {
    LARGE,
    MEDIUM,
    SMALL,
    NONHUB
}
