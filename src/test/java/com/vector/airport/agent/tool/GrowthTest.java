package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** growth reads the FAA forecast in its forecast year, ten years past its base year. */
@FixtureDataTest
class GrowthTest {

    @Autowired
    Growth growth;

    @Test
    void growthIsTheForecastTenYearsPastItsBaseYear() {
        assertThat(growth.growth(List.of("BOS")).rows())
                .extracting("base_year", "forecast_year", "boardings", "forecast_boardings", "yearly_growth")
                .containsExactly(tuple(2024, 2034, 20_871_588L, 26_189_799L, 0.023));
    }
}
