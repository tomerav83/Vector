package com.vector.airport.agent.tool.score;

import com.vector.airport.agent.tool.Hub;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An airport's score as a terminal-expansion candidate, 0 to 100, and what makes it up; the model reads its keys in
 * snake case ({@code airport_id}, {@code best_rank}), as in every other tool's rows.
 *
 * @param name its name, which the page shows beside the code
 * @param hub its FAA hub size
 * @param boardings its boardings in the newest year of the FAA report
 * @param score the sum of its KPIs' points
 * @param rank its place by score among the airports ranked with it, 1 the best; airports with the same score share a
 *     place
 * @param peers how many airports the score ranks it among, itself included
 * @param bestRank its best rank as the weights vary: all equal, or one KPI left out at a time
 * @param worstRank its worst rank as the weights vary
 * @param contributions each KPI's part in the score
 */
@JsonNaming(SnakeCaseStrategy.class)
public record Score(
        String airportId,
        String name,
        Hub hub,
        Long boardings,
        double score,
        int rank,
        int peers,
        int bestRank,
        int worstRank,
        List<Contribution> contributions) {}
