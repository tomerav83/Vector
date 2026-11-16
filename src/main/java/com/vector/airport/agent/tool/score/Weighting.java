package com.vector.airport.agent.tool.score;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * How much each KPI counts in a score, e.g. the defaults growth 30, scale 25, headroom 20, fare premium 15 and load
 * factor 10; weights count relative to each other, a KPI the map lacks counts 0, and a weight below 0 or none above 0
 * fails.
 */
record Weighting(Map<Kpi, Integer> weights) {

    Weighting {
        weights = Map.copyOf(weights);
        if (weights.values().stream().anyMatch(weight -> weight < 0)
                || weights.values().stream().noneMatch(weight -> weight > 0)) {
            throw new IllegalArgumentException(
                    "Weights are 0 or more and one is above 0, e.g. [{kpi: GROWTH, weight: 50}]; got " + weights);
        }
    }

    /** The default weights with the analyst's changes, e.g. growth 50 and the others as they are. */
    static Weighting create(List<Weight> changes) {
        Map<Kpi, Integer> changed =
                changes.stream().collect(toMap(Weight::kpi, Weight::weight, (earlier, later) -> later));
        return create(kpi -> changed.getOrDefault(kpi, kpi.weight));
    }

    /**
     * The weightings a rank's range covers, as the OECD handbook asks: every KPI weighing the same, then each KPI left
     * out in turn while another keeps a weight.
     */
    Stream<Weighting> variations() {
        Stream<Weighting> leftOut = Arrays.stream(Kpi.values())
                .filter(out -> weight(out) < total())
                .map(out -> create(kpi -> kpi == out ? 0 : weight(kpi)));
        return Stream.concat(Stream.of(create(kpi -> 1)), leftOut);
    }

    /**
     * Each KPI's share of the airport's score in percent: its weight over the weights of the KPIs the airport has a
     * value for, so a KPI without one gives its weight to the others; empty when none of those weighs above 0.
     */
    Optional<Map<Kpi, Double>> shares(Candidate airport) {
        int counted = airport.kpis().keySet().stream().mapToInt(this::weight).sum();
        return counted == 0
                ? Optional.empty()
                : Optional.of(airport.kpis().keySet().stream()
                        .collect(toMap(identity(), kpi -> 100.0 * weight(kpi) / counted)));
    }

    private static Weighting create(ToIntFunction<Kpi> weight) {
        return new Weighting(Arrays.stream(Kpi.values()).collect(toMap(identity(), weight::applyAsInt)));
    }

    private int weight(Kpi kpi) {
        return weights.getOrDefault(kpi, 0);
    }

    private int total() {
        return weights.values().stream().mapToInt(Integer::intValue).sum();
    }
}
