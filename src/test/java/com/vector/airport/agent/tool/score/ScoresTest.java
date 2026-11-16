package com.vector.airport.agent.tool.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.vector.airport.FixtureDataTest;
import com.vector.airport.agent.tool.Evidence;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code rank} tool on the fixture data (16 primary airports): golden scores that pin the {@code candidate} view
 * and the math together, and what the model sends and reads. The math's own specs, with literal inputs, are
 * {@link PeersTest} and {@link WeightingTest}.
 */
@FixtureDataTest
class ScoresTest {

    static final List<String> NEW_ENGLAND = List.of("BOS", "BDL", "PVD", "MHT", "ORH");

    @Autowired
    Scores scores;

    @Test
    void namedAirportsAreRankedAgainstEachOther() {
        // PVD leads on growth and headroom (60 points of 100), BOS on scale, fare premium and load factor (50 points)
        List<Score> pair = scores.rank(null, List.of("PVD", "BOS"), null).rows();

        assertThat(pair)
                .extracting(Score::airportId, Score::rank, Score::peers, Score::score)
                .containsExactly(tuple("BOS", 1, 2, 50.0), tuple("PVD", 1, 2, 50.0));
        assertThat(pair.getLast().contributions())
                .extracting(Contribution::kpi, Contribution::rank, Contribution::peers)
                .containsExactly(
                        tuple(Kpi.GROWTH, 1, 2),
                        tuple(Kpi.SCALE, 2, 2),
                        tuple(Kpi.HEADROOM, 1, 2),
                        tuple(Kpi.FARE_PREMIUM, 2, 2),
                        tuple(Kpi.LOAD_FACTOR, 2, 2));
    }

    @Test
    void anAirportShownIsRankedWithThePoolItIsShownAgainst() {
        assertThat(scores.rank(List.of("BOS"), List.of("PVD"), null).rows())
                .extracting(Score::airportId, Score::rank, Score::peers)
                .containsExactly(tuple("BOS", 1, 2));
    }

    @Test
    void oneAirportAloneIsRankedAmongEveryUsAirportWithAScore() {
        assertThat(scores.rank(List.of("BOS"), null, null).rows())
                .extracting(Score::airportId, Score::name, Score::boardings, Score::peers)
                .containsExactly(tuple("BOS", "Logan International", 21_021_153L, 16));
    }

    @Test
    void withoutAirportsTheAnswerIsTheTop10OfTheUs() {
        List<Score> top = scores.rank(null, null, null).rows();

        assertThat(top).hasSize(10).extracting(Score::peers).containsOnly(16);
        assertThat(top).extracting(Score::rank).isSorted();
    }

    @Test
    void withoutAirportsTheAnswerIsEveryAirportOfThePool() {
        assertThat(scores.rank(null, NEW_ENGLAND, null).rows())
                .extracting(Score::airportId)
                .containsExactlyInAnyOrderElementsOf(NEW_ENGLAND);
    }

    @Test
    void scoreIsTheSumOfEachKpisShareTimesItsPercentile() {
        // among the five of New England: PVD grows fastest, adds the third most, has the third least NAS delay, and the
        // lowest fare premium (of the four with one) and the fourth fullest flights
        Score pvd = score("PVD", NEW_ENGLAND, List.of());

        assertThat(pvd.contributions())
                .extracting(Contribution::kpi, Contribution::rank, Contribution::peers, Contribution::percentile)
                .containsExactly(
                        tuple(Kpi.GROWTH, 1, 5, 4.5 / 5),
                        tuple(Kpi.SCALE, 3, 5, 2.5 / 5),
                        tuple(Kpi.HEADROOM, 3, 5, 1 - 2.5 / 5),
                        tuple(Kpi.FARE_PREMIUM, 4, 4, 0.5 / 4),
                        tuple(Kpi.LOAD_FACTOR, 4, 5, 1.5 / 5));
        assertThat(pvd.score()).isCloseTo(30 * 0.9 + 25 * 0.5 + 20 * 0.5 + 15 * 0.125 + 10 * 0.3, within(1e-9));
    }

    @Test
    void fewerNasDelayMinutesMeanMoreHeadroom() {
        assertThat(score("MHT", NEW_ENGLAND, List.of()).contributions())
                .filteredOn(contribution -> contribution.kpi() == Kpi.HEADROOM)
                .extracting(Contribution::value, Contribution::rank, Contribution::percentile)
                .as("the fewest NAS minutes per arrival of the five New England airports")
                .containsExactly(tuple(1.62, 1, 1 - 0.5 / 5));
    }

    @Test
    void kpiWithoutAValueGivesItsWeightToTheOthers() {
        // DOT's fare table leaves out small airports such as Worcester
        assertThat(score("ORH", null, List.of()).contributions())
                .extracting(Contribution::kpi, Contribution::value, Contribution::rank, Contribution::share)
                .containsExactly(
                        tuple(Kpi.GROWTH, 0.0112, 13, 100.0 * 30 / 85),
                        tuple(Kpi.SCALE, 13196.0, 13, 100.0 * 25 / 85),
                        tuple(Kpi.HEADROOM, 2.04, 8, 100.0 * 20 / 85),
                        tuple(Kpi.FARE_PREMIUM, null, null, 0.0),
                        tuple(Kpi.LOAD_FACTOR, 0.9571, 2, 100.0 * 10 / 85));
    }

    @Test
    void weightsTheAnalystSetsReorderTheRanking() {
        List<String> medium = List.of("BDL", "ANC", "SNA", "BUR");

        assertThat(scores.rank(null, medium, null).rows())
                .extracting(Score::airportId)
                .containsExactly("SNA", "ANC", "BDL", "BUR");
        assertThat(scores.rank(null, medium, List.of(new Weight(Kpi.HEADROOM, 100)))
                        .rows())
                .extracting(Score::airportId)
                .containsExactly("SNA", "ANC", "BUR", "BDL");
    }

    @Test
    void rankRangeShowsHowFarARankMovesAsTheWeightsVary() {
        // BDL falls behind BUR when every KPI weighs the same, or growth or scale is left out
        assertThat(score("BDL", List.of("BDL", "ANC", "SNA", "BUR"), List.of()))
                .extracting(Score::rank, Score::bestRank, Score::worstRank)
                .containsExactly(3, 3, 4);
    }

    @Test
    void anAirportWithoutAValueForAnyWeightedKpiIsNotRankedAndTheAnswerSaysSo() {
        // ranking on fare premium alone: of the two, only Tallahassee has a fare figure
        List<Weight> faresOnly = Arrays.stream(Kpi.values())
                .map(kpi -> new Weight(kpi, kpi == Kpi.FARE_PREMIUM ? 100 : 0))
                .toList();

        Evidence<Score> evidence = scores.rank(null, List.of("ORH", "TLH"), faresOnly);

        assertThat(evidence.rows())
                .extracting(Score::airportId, Score::rank, Score::peers)
                .containsExactly(tuple("TLH", 1, 1));
        assertThat(evidence.limitations())
                .contains("Not ranked, as only primary airports with a value for a weighted KPI are: ORH.");
    }

    @Test
    void theAnswerGivesTheStateOfEveryKpisSource() {
        assertThat(scores.rank(List.of("BOS"), null, null).sources())
                .extracting(source -> source.get("name"))
                .containsExactly("bts-delays", "bts-segments", "dot-fares", "faa-boardings", "faa-forecast");
    }

    @Test
    void theModelReadsEachScoreInSnakeCaseAsInTheOtherTools() {
        assertThat(rank().call("{\"among\": [\"BOS\", \"PVD\"]}"))
                .contains("\"airport_id\":\"BOS\"", "\"name\":\"Logan International\"", "\"best_rank\":", "\"peers\":");
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

    /** {@code airport}'s score among {@code among}, or among every US airport when null. */
    private Score score(String airport, List<String> among, List<Weight> weights) {
        return scores.rank(List.of(airport), among, weights).rows().getFirst();
    }

    /** The tool as the model calls it: JSON in, JSON out. */
    private ToolCallback rank() {
        return Arrays.stream(ToolCallbacks.from(scores))
                .filter(tool -> tool.getToolDefinition().name().equals("rank"))
                .findFirst()
                .orElseThrow();
    }
}
