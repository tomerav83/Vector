package com.vector.airport.agent.tool;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The weights start from each KPI's default and take the analyst's changes; a KPI without a value gives its weight to
 * the others, and the rank range tries every KPI weighing the same and each KPI left out in turn.
 */
class WeightingTest {

    static final Weighting DEFAULTS = Weighting.create(List.of());

    @Test
    void theAnalystsChangesReplaceTheDefaultsTheLaterOneWinning() {
        Weighting weighting = Weighting.create(
                List.of(new Weight(Kpi.GROWTH, 50), new Weight(Kpi.GROWTH, 40), new Weight(Kpi.HEADROOM, 0)));

        assertThat(weighting.weights())
                .containsEntry(Kpi.GROWTH, 40)
                .containsEntry(Kpi.HEADROOM, 0)
                .containsEntry(Kpi.SCALE, Kpi.SCALE.weight);
    }

    @Test
    void aWeightBelowZeroOrNoWeightAboveZeroFails() {
        assertThatThrownBy(() -> Weighting.create(List.of(new Weight(Kpi.GROWTH, -5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weights are 0 or more and one is above 0");
        assertThatThrownBy(() -> new Weighting(Map.of(Kpi.GROWTH, 0, Kpi.SCALE, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weight(null, 50)).hasMessageContaining("names its KPI");
    }

    @Test
    void variationsWeighEveryKpiTheSameThenLeaveEachOutInTurn() {
        Weighting equal = new Weighting(Arrays.stream(Kpi.values()).collect(toMap(identity(), kpi -> 1)));
        Stream<Weighting> leftOut =
                Arrays.stream(Kpi.values()).map(kpi -> Weighting.create(List.of(new Weight(kpi, 0))));

        assertThat(DEFAULTS.variations())
                .containsExactlyElementsOf(
                        Stream.concat(Stream.of(equal), leftOut).toList());
    }

    @Test
    void theOnlyWeightedKpiIsNeverLeftOut() {
        assertThat(new Weighting(Map.of(Kpi.FARE_PREMIUM, 100)).variations())
                .extracting(weighting -> weighting.weights().get(Kpi.FARE_PREMIUM))
                .doesNotContain(0);
    }

    @Test
    void aKpiWithoutAValueGivesItsWeightToTheOthers() {
        Candidate airport = new Candidate("ORH", Hub.NONHUB, Map.of(Kpi.GROWTH, 0.0112, Kpi.SCALE, 13196.0));

        assertThat(DEFAULTS.shares(airport)).contains(Map.of(Kpi.GROWTH, 100.0 * 30 / 55, Kpi.SCALE, 100.0 * 25 / 55));
    }

    @Test
    void anAirportWithoutAValueForAnyWeightedKpiHasNoShares() {
        Candidate airport = new Candidate("ORH", Hub.NONHUB, Map.of(Kpi.GROWTH, 0.0112));

        assertThat(new Weighting(Map.of(Kpi.FARE_PREMIUM, 100)).shares(airport)).isEmpty();
    }
}
