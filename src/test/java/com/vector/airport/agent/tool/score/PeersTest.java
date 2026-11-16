package com.vector.airport.agent.tool.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.vector.airport.agent.tool.Hub;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A score is the weighted sum of an airport's KPI percentiles among its peers; its rank is 1 plus the number of peers
 * that score higher, and its range spans every KPI weighing the same and each KPI left out in turn.
 */
class PeersTest {

    static final Weighting DEFAULTS = Weighting.create(List.of());

    @Test
    void percentileIsTheShareOfThePeersBeatenATieCountingHalf() {
        Peers peers = new Peers(List.of(
                airport("A", Map.of(Kpi.GROWTH, 1.0)),
                airport("B", Map.of(Kpi.GROWTH, 2.0)),
                airport("C", Map.of(Kpi.GROWTH, 2.0)),
                airport("D", Map.of(Kpi.GROWTH, 3.0))));

        assertThat(peers.scores(DEFAULTS))
                .extracting(Score::airportId, score -> percentile(score, Kpi.GROWTH))
                .containsExactly(tuple("A", 0.125), tuple("B", 0.5), tuple("C", 0.5), tuple("D", 0.875));
    }

    @Test
    void aLowerValueBeatsAHigherOneWhereLowerIsBetter() {
        Peers peers =
                new Peers(List.of(airport("A", Map.of(Kpi.HEADROOM, 1.0)), airport("B", Map.of(Kpi.HEADROOM, 2.0))));

        assertThat(peers.scores(DEFAULTS))
                .extracting(Score::airportId, score -> percentile(score, Kpi.HEADROOM))
                .containsExactly(tuple("A", 0.75), tuple("B", 0.25));
    }

    @Test
    void scoreIsEachKpisShareTimesItsPercentileAndAKpiWithoutAValueGivesItsShareToTheOthers() {
        Peers peers = new Peers(List.of(
                airport("A", Map.of(Kpi.GROWTH, 3.0, Kpi.SCALE, 300.0)),
                airport("B", Map.of(Kpi.GROWTH, 1.0, Kpi.SCALE, 100.0)),
                airport("C", Map.of(Kpi.GROWTH, 2.0))));

        List<Score> scores = peers.scores(DEFAULTS);

        assertThat(scores.getFirst().contributions())
                .filteredOn(contribution -> contribution.value() != null)
                .extracting(Contribution::kpi, Contribution::share, Contribution::percentile)
                .containsExactly(tuple(Kpi.GROWTH, 100.0 * 30 / 55, 2.5 / 3), tuple(Kpi.SCALE, 100.0 * 25 / 55, 0.75));
        assertThat(scores.getFirst().score())
                .isCloseTo(100.0 * 30 / 55 * 2.5 / 3 + 100.0 * 25 / 55 * 0.75, within(1e-9));
        assertThat(scores.getLast().contributions())
                .extracting(Contribution::kpi, Contribution::value, Contribution::share)
                .startsWith(tuple(Kpi.GROWTH, 2.0, 100.0), tuple(Kpi.SCALE, null, 0.0));
    }

    @Test
    void peersWithTheSameScoreShareARankAndTheNextRankSkips() {
        Peers peers = new Peers(List.of(
                airport("A", Map.of(Kpi.GROWTH, 1.0)),
                airport("B", Map.of(Kpi.GROWTH, 2.0)),
                airport("C", Map.of(Kpi.GROWTH, 2.0)),
                airport("D", Map.of(Kpi.GROWTH, 3.0))));

        assertThat(peers.scores(DEFAULTS))
                .extracting(Score::airportId, Score::rank)
                .containsExactly(tuple("A", 4), tuple("B", 2), tuple("C", 2), tuple("D", 1));
    }

    @Test
    void anAirportWithoutAValueForAnyWeightedKpiIsNotRanked() {
        Peers peers = new Peers(List.of(
                airport("FARES", Map.of(Kpi.FARE_PREMIUM, 0.1)), airport("NO FARES", Map.of(Kpi.GROWTH, 0.02))));

        assertThat(peers.scores(new Weighting(Map.of(Kpi.FARE_PREMIUM, 1))))
                .extracting(Score::airportId, Score::rank, Score::peers)
                .containsExactly(tuple("FARES", 1, 1));
    }

    @Test
    void rankRangeSpansEveryKpiWeighingTheSameAndEachLeftOutInTurn() {
        // growth alone ranks A first, scale alone ranks B first; equal weights tie them
        Peers peers = new Peers(List.of(
                airport("A", Map.of(Kpi.GROWTH, 2.0, Kpi.SCALE, 100.0)),
                airport("B", Map.of(Kpi.GROWTH, 1.0, Kpi.SCALE, 200.0))));

        assertThat(peers.scores(DEFAULTS))
                .extracting(Score::airportId, Score::rank, Score::bestRank, Score::worstRank)
                .containsExactly(tuple("A", 1, 1, 2), tuple("B", 2, 1, 2));
    }

    @Test
    void aWeightingThatLeavesAnAirportWithoutAScoreLeavesItsRangeAlone() {
        // with growth left out, C has no weighted value: that weighting does not rank it first, or at all
        Peers peers = new Peers(List.of(
                airport("A", Map.of(Kpi.GROWTH, 3.0, Kpi.SCALE, 300.0)),
                airport("B", Map.of(Kpi.GROWTH, 2.0, Kpi.SCALE, 200.0)),
                airport("C", Map.of(Kpi.GROWTH, 1.0))));

        assertThat(peers.scores(DEFAULTS).getLast())
                .extracting(Score::rank, Score::bestRank, Score::worstRank)
                .containsExactly(3, 3, 3);
    }

    private static Candidate airport(String id, Map<Kpi, Double> kpis) {
        return new Candidate(id, id, Hub.LARGE, null, kpis);
    }

    private static Double percentile(Score score, Kpi kpi) {
        return score.contributions().stream()
                .filter(contribution -> contribution.kpi() == kpi)
                .findFirst()
                .orElseThrow()
                .percentile();
    }
}
