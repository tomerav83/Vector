package com.vector.airport.agent.tool;

import java.util.Map;
import java.util.Optional;

/**
 * An airport the score ranks, as the {@code candidate} view has it, e.g. BOS, a large hub, with its KPI values; a KPI
 * its data has no value for, e.g. fare premium in Alaska, is not in {@code kpis}.
 */
record Candidate(String airportId, Hub hub, Map<Kpi, Double> kpis) {

    Candidate {
        kpis = Map.copyOf(kpis);
    }

    Optional<Double> value(Kpi kpi) {
        return Optional.ofNullable(kpis.get(kpi));
    }
}
