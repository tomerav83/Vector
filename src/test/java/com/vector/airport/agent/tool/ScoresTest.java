package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.vector.airport.FixtureDataTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code rank} tool on the fixture data (3 large, 5 medium, 4 small and 4 nonhub airports): golden scores that pin
 * the {@code candidate} view and the math together, and what the model sends and reads. The math's own specs, with
 * literal inputs, are {@link PeersTest} and {@link WeightingTest}.
 */
@FixtureDataTest
class ScoresTest {

    @Autowired
    Scores scores;

    @Test
    void eachAirportIsRankedAmongTheAirportsOfItsHubSizeLargestFirst() {
        assertThat(scores.rank(List.of("MHT", "BDL", "ORH", "PVD", "BOS"), null).rows())
                .extracting("airportId", "hub", "rank", "peers")
                .containsExactly(
                        tuple("BOS", Hub.LARGE, 3, 3),
                        tuple("BDL", Hub.MEDIUM, 3, 5),
                        tuple("PVD", Hub.SMALL, 2, 4),
                        tuple("MHT", Hub.SMALL, 3, 4),
                        tuple("ORH", Hub.NONHUB, 2, 4));
    }

    @Test
    void scoreIsTheSumOfEachKpisShareTimesItsPercentile() {
        Score pvd = score("PVD", List.of());

        assertThat(pvd.contributions())
                .extracting(Contribution::kpi, Contribution::share, Contribution::percentile)
                .containsExactly(
                        tuple(Kpi.GROWTH, 30.0, 0.625),
                        tuple(Kpi.SCALE, 25.0, 0.875),
                        tuple(Kpi.HEADROOM, 20.0, 0.375),
                        tuple(Kpi.FARE_PREMIUM, 15.0, 0.5),
                        tuple(Kpi.LOAD_FACTOR, 10.0, 0.5));
        assertThat(pvd.score()).isCloseTo(30 * 0.625 + 25 * 0.875 + 20 * 0.375 + 15 * 0.5 + 10 * 0.5, within(1e-9));
    }

    @Test
    void fewerNasDelayMinutesMeanMoreHeadroom() {
        assertThat(score("MHT", List.of()).contributions())
                .filteredOn(contribution -> contribution.kpi() == Kpi.HEADROOM)
                .extracting(Contribution::value, Contribution::percentile)
                .as("the second fewest NAS minutes per arrival of the 4 small airports, after Guam's 0.48")
                .containsExactly(tuple(1.62, 0.625));
    }

    @Test
    void kpiWithoutAValueGivesItsWeightToTheOthers() {
        // DOT's fare table leaves out small airports such as Worcester
        assertThat(score("ORH", List.of()).contributions())
                .extracting(Contribution::kpi, Contribution::value, Contribution::share)
                .containsExactly(
                        tuple(Kpi.GROWTH, 0.0112, 100.0 * 30 / 85),
                        tuple(Kpi.SCALE, 13196.0, 100.0 * 25 / 85),
                        tuple(Kpi.HEADROOM, 2.04, 100.0 * 20 / 85),
                        tuple(Kpi.FARE_PREMIUM, null, 0.0),
                        tuple(Kpi.LOAD_FACTOR, 0.9571, 100.0 * 10 / 85));
    }

    @Test
    void weightsTheAnalystSetsReorderTheRanking() {
        List<String> medium = List.of("BDL", "ANC", "SNA", "BUR");

        assertThat(scores.rank(medium, null).rows())
                .extracting("airportId")
                .containsExactly("SNA", "ANC", "BDL", "BUR");
        assertThat(scores.rank(medium, List.of(new Weight(Kpi.HEADROOM, 100))).rows())
                .extracting("airportId")
                .containsExactly("SNA", "ANC", "BUR", "BDL");
    }

    @Test
    void rankRangeShowsHowFarARankMovesAsTheWeightsVary() {
        assertThat(score("BDL", List.of()))
                .extracting(Score::rank, Score::bestRank, Score::worstRank)
                .containsExactly(3, 3, 4);
    }

    @Test
    void anAirportWithoutAValueForAnyWeightedKpiIsNotRankedAndTheAnswerSaysSo() {
        // ranking on fare premium alone: of the nonhubs, only Tallahassee has a fare figure
        List<Weight> faresOnly = Arrays.stream(Kpi.values())
                .map(kpi -> new Weight(kpi, kpi == Kpi.FARE_PREMIUM ? 100 : 0))
                .toList();

        Evidence evidence = scores.rank(List.of("ORH", "TLH"), faresOnly);

        assertThat(evidence.rows()).extracting("airportId", "rank", "peers").containsExactly(tuple("TLH", 1, 1));
        assertThat(evidence.caveats())
                .contains("ORH is not ranked: only primary airports with a value for a weighted KPI are.");
    }

    @Test
    void theAnswerGivesTheStateOfEveryKpisSource() {
        assertThat(scores.rank(List.of("BOS"), null).sources())
                .extracting(source -> source.get("name"))
                .containsExactly("bts-delays", "bts-segments", "dot-fares", "faa-boardings", "faa-forecast");
    }

    @Test
    void theModelReadsEachScoreInSnakeCaseAsInTheOtherTools() {
        assertThat(rank().call("{\"airports\": [\"BOS\"]}"))
                .contains("\"airport_id\":\"BOS\"", "\"best_rank\":", "\"worst_rank\":", "\"share\":");
    }

    @Test
    void aBadWeightTellsTheModelWhatAWeightIs() {
        assertThatThrownBy(() ->
                        rank().call("{\"airports\": [\"BOS\"], \"weights\": [{\"kpi\": \"GROWTH\", \"weight\": -5}]}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Weights are 0 or more and one is above 0");
        assertThatThrownBy(() -> rank().call("{\"airports\": [\"BOS\"], \"weights\": [{\"weight\": 50}]}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("A weight names its KPI");
    }

    private Score score(String airport, List<Weight> weights) {
        return (Score) scores.rank(List.of(airport), weights).rows().getFirst();
    }

    /** The tool as the model calls it: JSON in, JSON out. */
    private ToolCallback rank() {
        return Arrays.stream(ToolCallbacks.from(scores))
                .filter(tool -> tool.getToolDefinition().name().equals("rank"))
                .findFirst()
                .orElseThrow();
    }
}
