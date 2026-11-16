package com.vector.airport.agent.tool.score;

import com.vector.airport.agent.tool.Hub;
import java.util.Map;
import java.util.Optional;

/**
 * An airport the score ranks, as the {@code candidate} view has it, e.g. BOS, a large hub, with its KPI values; a KPI
 * its data has no value for, e.g. fare premium in Alaska, is not in {@code kpis}.
 *
 * @param name its name, e.g. Boston Logan International
 * @param boardings its boardings in the newest year of the FAA report; null when the report lacks it
 */
record Candidate(String airportId, String name, Hub hub, Long boardings, Map<Kpi, Double> kpis) {

    Candidate {
        kpis = Map.copyOf(kpis);
    }

    Optional<Double> value(Kpi kpi) {
        return Optional.ofNullable(kpis.get(kpi));
    }
}
