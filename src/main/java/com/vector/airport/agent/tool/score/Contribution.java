package com.vector.airport.agent.tool.score;

/**
 * One KPI's part in an airport's score.
 *
 * @param value the airport's figure, e.g. 0.81 for load factor; null when the data has none, and then it adds nothing
 * @param rank its place on this KPI among the airports ranked with it that have a value, 1 the best; null without a
 *     value
 * @param peers how many airports ranked with it have a value for this KPI
 * @param percentile the share of those airports it beats, 0 to 1, a tie counting half; a lower value beats a higher one
 *     where lower is better
 * @param share its share of the score in percent: its weight over the weights of the KPIs the airport has a value for
 * @param points share × percentile: what it adds to the score
 */
public record Contribution(
        Kpi kpi, Double value, Integer rank, int peers, Double percentile, double share, double points) {}
