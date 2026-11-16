package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/** find answers with the commercial airports a code, name, city, metro area, state or hub size names, busiest first. */
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
        assertThat(airports.find(query, null, null).rows())
                .as(why)
                .extracting("airport_id")
                .containsExactly(airportId);
    }

    @Test
    void findGivesTheMainAirportOfTheMetroArea() {
        assertThat(airports.find("SNA", null, null).rows()).extracting("metro").containsExactly("LAX");
    }

    @Test
    void cityFindsEveryAirportOfTheMetroAreaItsMainAirportIsIn() {
        assertThat(airports.find("los angeles", null, null).rows())
                .extracting("airport_id")
                .contains("LAX", "SNA", "BUR");
    }

    @Test
    void findsTheAirportsOfAHubSize() {
        assertThat(airports.find(null, null, List.of(Hub.LARGE)).rows())
                .extracting("hub")
                .isNotEmpty()
                .containsOnly(Hub.LARGE.name());
    }

    @Test
    void findsTheCommercialAirportsOfStatesBusiestFirst() {
        assertThat(airports.find(null, List.of("ma", "ct", "ri", "nh", "me", "vt"), null)
                        .rows())
                .extracting("airport_id")
                .containsExactly("BOS", "BDL", "PVD", "MHT", "ORH");
    }

    /** The query is a bind value, so a quote in it cannot end the SQL string (OWASP: parameterized queries). */
    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            x' or 1=1 --                                         | a condition that is always true
            ') union select * from read_text('/etc/passwd') --   | a second query reading a file
            """)
    void queryThatLooksLikeSqlFindsNothing(String query, String attempt) {
        assertThat(airports.find(query, null, null).rows()).as(attempt).isEmpty();
    }

    @Test
    void countCountsTheCommercialAirportsThatFindLists() {
        assertThat(airports.count(List.of("ma", "ct", "ri", "nh", "me", "vt")).rows())
                .extracting("commercial_airports")
                .containsExactly(5);
    }

    @Test
    void citesTheSourcesOfTheAirportsTheirCodesBoardingsAndMetros() {
        assertThat(airports.find("BOS", null, null).sources())
                .extracting("name")
                .containsExactly("bts-airports", "faa-boardings", "faa-forecast", "faa-register");
    }
}
