package com.vector.airport.agent.tool;

/**
 * One KPI's part in an airport's score.
 *
 * @param value the airport's figure, e.g. 0.81 for load factor; null when the data has none, and then it adds nothing
 * @param percentile the share of its peers it beats, 0 to 1, a tie counting half; a lower value beats a higher one
 *     where lower is better
 * @param share its share of the score in percent: its weight over the weights of the KPIs the airport has a value for
 * @param points share × percentile: what it adds to the score
 */
record Contribution(Kpi kpi, Double value, Double percentile, double share, double points) {}
