package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** runways reads the FAA register's runways of each airport, and flightLimits the FAA's limit on its flights. */
@FixtureDataTest
class RunwaysTest {

    @Autowired
    Runways runways;

    @Test
    void runwaysLongestFirst() {
        assertThat(runways.runways(List.of("BDL")).rows())
                .extracting("runway_id", "length_ft")
                .containsExactly(tuple("06/24", 9510), tuple("15/33", 6847));
    }

    @Test
    void flightLimitIsWhatTheFaaListsAndNoneForAnAirportItDoesNot() {
        Evidence<Map<String, Object>> limits = runways.flightLimits(List.of("LAX", "BOS"));

        assertThat(limits.rows())
                .extracting("airport_id", "flight_limit")
                .containsExactly(tuple("BOS", "NONE"), tuple("LAX", "MANAGED"));
        assertThat(limits.sources()).extracting("name").containsExactly("faa-slots-capped", "faa-slots-managed");
    }
}
