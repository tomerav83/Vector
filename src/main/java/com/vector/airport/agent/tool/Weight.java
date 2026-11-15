package com.vector.airport.agent.tool;

import java.util.Objects;

/**
 * A weight the analyst sets for a KPI, e.g. 50 for {@link Kpi#GROWTH}; weights count relative to each other, and one
 * without a KPI fails.
 */
record Weight(Kpi kpi, int weight) {

    Weight {
        Objects.requireNonNull(kpi, "A weight names its KPI, e.g. {kpi: GROWTH, weight: 50}");
    }
}
