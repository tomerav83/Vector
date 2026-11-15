package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** congestion reads the newest 12 months of on-time data, each airport against the US and its delay by cause. */
@FixtureDataTest
class CongestionTest {

    @Autowired
    Congestion congestion;

    @Test
    void congestionSetsEachAirportAgainstTheUsAndSplitsItsDelayByCause() {
        assertThat(congestion.congestion(List.of("SNA", "LAX")).rows())
                .extracting("airport_id", "delayed_share", "us_delayed_share", "nas_share", "nas_minutes_per_arrival")
                .containsExactly(
                        tuple("LAX", 0.1898, 0.2238, 0.1525, 2.01), tuple("SNA", 0.1816, 0.2238, 0.1715, 1.69));
    }
}
