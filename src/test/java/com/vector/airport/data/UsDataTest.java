package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.vector.airport.FixtureDataTest;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Each US source's SQL on trimmed copies of its real files, against facts checked by hand in them: a test is named
 * after the source whose {@code sql/<source>.sql} it checks, so a broken file fails the tests with its name.
 */
@FixtureDataTest
class UsDataTest {

    @Autowired
    DSLContext db;

    @Autowired
    DataProperties properties;

    /** One test per source, named after it: a failure names the source and why it was left out, e.g. DuckDB's error. */
    @TestFactory
    Stream<DynamicTest> everySourceLoadedItsLockedFiles() {
        return properties.sources().keySet().stream()
                .map(source -> dynamicTest(
                        source,
                        () -> assertThat(row(source))
                                .as(source)
                                .containsEntry("problem", null)
                                .containsEntry("matches_lock", true)));
    }

    @Test
    void noCodePointsAtTwoAirports() {
        assertThat(db.fetch("select code, kind from airport_code group by all having count(distinct airport_id) > 1")
                        .intoMaps())
                .isEmpty();
    }

    @Test
    void faaRegisterKeysAirportsByTheirFaaLocationId() {
        assertThat(value(String.class, "select icao from airport where airport_id = 'BOS'"))
                .isEqualTo("KBOS");
        assertThat(value(String.class, "select distinct country from airport")).isEqualTo("US");
        assertThat(value(String.class, "select icao from airport where airport_id = '1G4'"))
                .as("Grand Canyon West has no ICAO code")
                .isNull();
        assertThat(value(String.class, "select region from airport where airport_id = 'BOS'"))
                .as("New England")
                .isEqualTo("ANE");
    }

    @Test
    void faaRegisterKeepsRunwaysInFeet() {
        assertThat(value(
                        Integer.class, "select length_ft from runway where airport_id = 'BDL' and runway_id = '06/24'"))
                .isEqualTo(9510);
    }

    @ParameterizedTest(name = "{0} shows as {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            IWA | AZA | BTS and FAA codes differ
            GUM | GUM | BTS files territories under state TT
            DJT | DJT | renamed from PBI in Aug 2026: the newest code shows
            """)
    void btsAirportsMatchesCodesByPositionNotByCode(String airportId, String iata, String why) {
        assertThat(iata(airportId)).as(why).isEqualTo(iata);
    }

    @Test
    void btsAirportsFindsAnOldCodeButNeverMatchesOnTheCodeAlone() {
        assertThat(airportFor("PBI")).as("DJT's code until Aug 2026").contains("DJT");
        assertThat(airportFor("A63"))
                .as("FAA A63 and BTS A63 are different places 1,100 km apart")
                .isEmpty();
    }

    @Test
    void btsSegmentsKeepPublishedUnitsClassesAndUnscheduledRows() {
        assertThat(value(
                        Double.class,
                        "select max(distance_mi) from segment where origin_id = 'BOS' and dest_id = 'LAX'"))
                .isEqualTo(2611.0);
        assertThat(db.fetch("select distinct service_class from segment").getValues(0, String.class))
                .containsExactlyInAnyOrder(
                        "SCHEDULED_PASSENGER", "SCHEDULED_CARGO", "CHARTER_PASSENGER", "CHARTER_CARGO");
        assertThat(value(
                        Long.class,
                        "select count(*) from segment where departures_scheduled = 0 and departures_performed > 0"))
                .as("flown but unscheduled rows stay: shortfall must see them to exclude them")
                .isPositive();
        assertThat(value(Long.class, "select count(*) from segment where dest_country <> 'US' and dest_id is null"))
                .as("foreign ends are kept, without an airport_id")
                .isPositive();
    }

    @Test
    void btsDelaysKeepMissingValuesAsNull() {
        assertThat(value(Long.class, "select count(*) from delay where delayed is null"))
                .as("TLH Nov 2025: one cancelled flight, delayed count not reported")
                .isPositive();
        assertThat(value(Long.class, "select count(*) from delay where airport_id = 'DJT'"))
                .as("delay rows still say PBI")
                .isPositive();
    }

    @Test
    void faaBoardingsGiveTheBoardingsAndTheHubSize() {
        assertThat(value(Long.class, "select boardings from boardings where airport_id = 'BOS' and year = 2025"))
                .isPositive();
        assertThat(value(String.class, "select hub from airport where airport_id = 'BOS'"))
                .isEqualTo("LARGE");
    }

    @Test
    void faaForecastTellsActualYearsFromForecastOnes() {
        assertThat(value(Boolean.class, "select is_forecast from forecast where airport_id = 'BOS' and year = 2024"))
                .isFalse();
        assertThat(value(Boolean.class, "select is_forecast from forecast where airport_id = 'BOS' and year = 2045"))
                .isTrue();
    }

    @Test
    void faaForecastGroupsEachMetroAreaUnderItsHub() {
        assertThat(db.fetch("select airport_id from metro where hub_id = 'LAX'").getValues(0, String.class))
                .contains("LAX", "SNA", "BUR");
    }

    @Test
    void dotFaresGiveThePremiumAsAFraction() {
        assertThat(value(Double.class, "select premium from fare_premium where airport_id = 'SNA'"))
                .isCloseTo(0.081, within(0.001));
    }

    @Test
    void dotFaresCoverTheLower48Only() {
        assertThat(value(Long.class, "select count(*) from fare_premium where airport_id = 'ANC'"))
                .isZero();
    }

    @Test
    void finishShowsTheBusierCodeOfAnAirportWithTwo() {
        assertThat(iata("NYL")).as("two BTS ids at Yuma").isEqualTo("YUM");
    }

    private Map<String, Object> row(String source) {
        return db.fetchSingle("select * from source where name = ?", source).intoMap();
    }

    private <T> T value(Class<T> type, String sql, Object... bindings) {
        return db.fetchSingle(sql, bindings).get(0, type);
    }

    private String iata(String airportId) {
        return value(String.class, "select iata from airport where airport_id = ?", airportId);
    }

    private Optional<String> airportFor(String btsCode) {
        return db.fetchOptional("select airport_id from airport_code where kind = 'BTS' and code = ?", btsCode)
                .map(row -> row.get(0, String.class));
    }
}
