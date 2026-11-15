package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.DOUBLE;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** traffic and longHaul read the newest 12 months of T-100 segments, with the state of that source and its caveats. */
@FixtureDataTest
class TrafficTest {

    @Autowired
    Traffic traffic;

    @Test
    void trafficCoversTheNewest12Months() {
        assertThat(traffic.traffic(List.of("SFO")).rows())
                .extracting("first_month", "last_month", "load_factor", "shortfall")
                .containsExactly(tuple("2025-02", "2026-01", 0.8095, 0.0151));
    }

    @Test
    void longHaulSplitsPassengerAndCargoFlightsAt3000MilesUnlessToldOtherwise() {
        assertThat(traffic.longHaul(List.of("ANC"), null).rows())
                .extracting("miles", "long_haul_share", "passenger_long_haul_share", "cargo_long_haul_share")
                .containsExactly(tuple(3000, 0.8486, 0.154, 0.8934));
    }

    @Test
    void longHaulCountsMoreFlightsAtAShorterDistance() {
        Map<String, Object> anc = traffic.longHaul(List.of("ANC"), 1000).rows().getFirst();

        assertThat(anc).containsEntry("miles", 1000);
        assertThat(anc).extractingByKey("long_haul_share", as(DOUBLE)).isGreaterThan(0.8486);
    }

    @Test
    void everyAnswerCarriesTheStateOfItsSourceAndItsCaveats() {
        Evidence<Map<String, Object>> answer = traffic.traffic(List.of("SFO"));

        assertThat(answer.sources())
                .extracting("name", "period", "problem")
                .containsExactly(tuple("bts-segments", "2026-01-01", null));
        assertThat(List.of(answer, traffic.longHaul(List.of("SFO"), null)))
                .as("both read T-100")
                .allSatisfy(evidence -> assertThat(evidence.caveats())
                        .contains("The newest month can still lack late-reporting airlines."));
    }
}
