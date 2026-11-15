package com.vector.airport.agent.tool;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An airport's score as a terminal-expansion candidate, 0 to 100, and what makes it up; the model reads its keys in
 * snake case ({@code airport_id}, {@code best_rank}), as in every other tool's rows.
 *
 * @param hub its FAA hub size; its peers are the US airports of the same size
 * @param score the sum of its KPIs' points
 * @param rank its place among its peers by score, 1 the best; peers with the same score share a place
 * @param peers how many airports of its hub size the score ranks, itself included
 * @param bestRank its best rank as the weights vary: all equal, or one KPI left out at a time
 * @param worstRank its worst rank as the weights vary
 * @param contributions each KPI's part in the score
 */
@JsonNaming(SnakeCaseStrategy.class)
record Score(
        String airportId,
        Hub hub,
        double score,
        int rank,
        int peers,
        int bestRank,
        int worstRank,
        List<Contribution> contributions) {}
