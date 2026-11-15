package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** runways reads the FAA register's runways of each airport. */
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
}
