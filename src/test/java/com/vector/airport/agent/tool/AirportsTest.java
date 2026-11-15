package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/** find answers with the commercial airports a code, name, city or state names, busiest first. */
@FixtureDataTest
class AirportsTest {

    @Autowired
    Airports airports;

    @ParameterizedTest(name = "{0} finds {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            KSFO      | SFO | its ICAO code
            AZA       | IWA | Mesa Gateway's airline code
            Logan     | BOS | its name
            santa ana | SNA | its city
            """)
    void findsAnAirportByAnyOfItsCodesItsNameOrItsCity(String query, String airportId, String why) {
        assertThat(airports.find(query, null).rows())
                .as(why)
                .extracting("airport_id")
                .containsExactly(airportId);
    }

    @Test
    void findGivesTheMainAirportOfTheMetroArea() {
        assertThat(airports.find("SNA", null).rows()).extracting("metro").containsExactly("LAX");
    }

    @Test
    void findsTheCommercialAirportsOfStatesBusiestFirst() {
        assertThat(airports.find(null, List.of("ma", "ct", "ri", "nh", "me", "vt"))
                        .rows())
                .extracting("airport_id")
                .containsExactly("BOS", "BDL", "PVD", "MHT", "ORH");
    }

    @Test
    void citesTheSourcesOfTheAirportsTheirCodesBoardingsAndMetros() {
        assertThat(airports.find("BOS", null).sources())
                .extracting("name")
                .containsExactly("bts-airports", "faa-boardings", "faa-forecast", "faa-register");
    }
}
