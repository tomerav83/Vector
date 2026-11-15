package com.vector.airport.agent.tool;

import java.util.Locale;

/**
 * What makes an airport a good bet for terminal expansion, each a column of the {@code candidate} view in
 * {@code sql/schema.sql}. Demand weighs most, since a terminal earns from passengers and the FAA's forecast is demand
 * without capacity limits. Headroom counts against an airport whose runways and airspace are full: new gates there add
 * no flights.
 *
 * @param weight its default weight; weights count relative to each other
 * @param higherIsBetter false when a lower value is the stronger case
 * @param source the source its value comes from, as {@code application-us.yml} names it
 * @param meaning what the value is, for the model to explain a score
 */
enum Kpi {
    GROWTH(30, true, "faa-forecast", "yearly growth of the boardings the FAA forecasts for the next 10 years"),
    SCALE(25, true, "faa-forecast", "boardings added in those 10 years"),
    HEADROOM(
            20,
            false,
            "bts-delays",
            "NAS (air traffic system) delay minutes per arrival over the newest 12 months; fewer is more room"),
    FARE_PREMIUM(
            15, true, "dot-fares", "how much more than the US average passengers pay for trips of the same distance"),
    LOAD_FACTOR(10, true, "bts-segments", "passengers per seat on scheduled flights over the newest 12 months");

    final int weight;
    final boolean higherIsBetter;
    final String source;
    final String meaning;

    Kpi(int weight, boolean higherIsBetter, String source, String meaning) {
        this.weight = weight;
        this.higherIsBetter = higherIsBetter;
        this.source = source;
        this.meaning = meaning;
    }

    /** Its column in the {@code candidate} view: the constant in lower case, e.g. {@code fare_premium}. */
    String column() {
        return name().toLowerCase(Locale.ROOT);
    }
}
