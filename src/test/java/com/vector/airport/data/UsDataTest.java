package com.vector.airport.data;

import static com.vector.airport.view.tables.Airport.AIRPORT;
import static com.vector.airport.view.tables.AirportCode.AIRPORT_CODE;
import static com.vector.airport.view.tables.Boardings.BOARDINGS;
import static com.vector.airport.view.tables.Delay.DELAY;
import static com.vector.airport.view.tables.FarePremium.FARE_PREMIUM;
import static com.vector.airport.view.tables.FlightLimit.FLIGHT_LIMIT;
import static com.vector.airport.view.tables.Forecast.FORECAST;
import static com.vector.airport.view.tables.Metro.METRO;
import static com.vector.airport.view.tables.Runway.RUNWAY;
import static com.vector.airport.view.tables.Segment.SEGMENT;
import static com.vector.airport.view.tables.Source.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectDistinct;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.vector.airport.FixtureDataTest;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.TableField;
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
    DataProperties properties;

    @Autowired
    DSLContext db;

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
    void faaRegisterKeysAirportsByTheirFaaLocationId() {
        assertThat(value(AIRPORT.ICAO, AIRPORT.AIRPORT_ID.eq("BOS"))).isEqualTo("KBOS");
        assertThat(db.fetchValue(selectDistinct(AIRPORT.COUNTRY).from(AIRPORT))).isEqualTo("US");
        assertThat(value(AIRPORT.ICAO, AIRPORT.AIRPORT_ID.eq("1G4")))
                .as("Grand Canyon West has no ICAO code")
                .isNull();
        assertThat(value(AIRPORT.REGION, AIRPORT.AIRPORT_ID.eq("BOS")))
                .as("New England")
                .isEqualTo("ANE");
    }

    @Test
    void faaRegisterKeepsRunwaysInFeet() {
        assertThat(value(RUNWAY.LENGTH_FT, RUNWAY.AIRPORT_ID.eq("BDL").and(RUNWAY.RUNWAY_ID.eq("06/24"))))
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
        assertThat(db.fetchValue(select(max(SEGMENT.DISTANCE_MI))
                        .from(SEGMENT)
                        .where(SEGMENT.ORIGIN_ID.eq("BOS"), SEGMENT.DEST_ID.eq("LAX"))))
                .isEqualTo(2611.0);
        assertThat(db.fetchValues(selectDistinct(SEGMENT.SERVICE_CLASS).from(SEGMENT)))
                .containsExactlyInAnyOrder(
                        "SCHEDULED_PASSENGER", "SCHEDULED_CARGO", "CHARTER_PASSENGER", "CHARTER_CARGO");
        assertThat(db.fetchCount(SEGMENT, SEGMENT.DEPARTURES_SCHEDULED.eq(0L).and(SEGMENT.DEPARTURES_PERFORMED.gt(0L))))
                .as("flown but unscheduled rows stay: shortfall must see them to exclude them")
                .isPositive();
        assertThat(db.fetchCount(SEGMENT, SEGMENT.DEST_COUNTRY.ne("US").and(SEGMENT.DEST_ID.isNull())))
                .as("foreign ends are kept, without an airport_id")
                .isPositive();
    }

    @Test
    void btsDelaysKeepMissingValuesAsNull() {
        assertThat(db.fetchCount(DELAY, DELAY.DELAYED.isNull()))
                .as("TLH Nov 2025: one cancelled flight, delayed count not reported")
                .isPositive();
        assertThat(db.fetchCount(DELAY, DELAY.AIRPORT_ID.eq("DJT")))
                .as("delay rows still say PBI")
                .isPositive();
    }

    @Test
    void faaBoardingsGiveTheBoardingsAndTheHubSize() {
        assertThat(value(BOARDINGS.BOARDINGS_, BOARDINGS.AIRPORT_ID.eq("BOS").and(BOARDINGS.YEAR.eq(2025))))
                .isPositive();
        assertThat(value(AIRPORT.HUB, AIRPORT.AIRPORT_ID.eq("BOS"))).isEqualTo("LARGE");
    }

    @Test
    void faaForecastTellsActualYearsFromForecastOnes() {
        assertThat(value(FORECAST.IS_FORECAST, FORECAST.AIRPORT_ID.eq("BOS").and(FORECAST.YEAR.eq(2024))))
                .isFalse();
        assertThat(value(FORECAST.IS_FORECAST, FORECAST.AIRPORT_ID.eq("BOS").and(FORECAST.YEAR.eq(2045))))
                .isTrue();
    }

    @Test
    void faaForecastGroupsEachMetroAreaUnderItsHub() {
        assertThat(db.fetchValues(select(METRO.AIRPORT_ID).from(METRO).where(METRO.HUB_ID.eq("LAX"))))
                .contains("LAX", "SNA", "BUR");
    }

    @Test
    void faaSlotsPagesNameTheAirportsTheFaaLimits() {
        assertThat(db.selectFrom(FLIGHT_LIMIT).fetchMap(FLIGHT_LIMIT.AIRPORT_ID, FLIGHT_LIMIT.FLIGHT_LIMIT_))
                .as("Level 3, Level 2, and Level 2 for the international terminal only")
                .isEqualTo(Map.of(
                        "JFK",
                        "CAPPED",
                        "LGA",
                        "CAPPED",
                        "DCA",
                        "CAPPED",
                        "ORD",
                        "MANAGED",
                        "LAX",
                        "MANAGED",
                        "EWR",
                        "MANAGED",
                        "SFO",
                        "MANAGED",
                        "MCO",
                        "TERMINAL",
                        "SEA",
                        "TERMINAL"));
    }

    @Test
    void dotFaresGiveThePremiumAsAFraction() {
        assertThat(value(FARE_PREMIUM.PREMIUM, FARE_PREMIUM.AIRPORT_ID.eq("SNA")))
                .isCloseTo(0.081, within(0.001));
    }

    @Test
    void dotFaresCoverTheLower48Only() {
        assertThat(db.fetchCount(FARE_PREMIUM, FARE_PREMIUM.AIRPORT_ID.eq("ANC")))
                .isZero();
    }

    @Test
    void finishShowsTheBusierCodeOfAnAirportWithTwo() {
        assertThat(iata("NYL")).as("two BTS ids at Yuma").isEqualTo("YUM");
    }

    private Map<String, Object> row(String source) {
        return db.fetchSingle(SOURCE, SOURCE.NAME.eq(source)).intoMap();
    }

    /** The value of {@code column} in its table's one row {@code where} keeps. */
    private <T> T value(TableField<?, T> column, Condition where) {
        return db.fetchSingle(select(column).from(column.getTable()).where(where))
                .value1();
    }

    private String iata(String airportId) {
        return value(AIRPORT.IATA, AIRPORT.AIRPORT_ID.eq(airportId));
    }

    private Optional<String> airportFor(String btsCode) {
        return db.fetchOptional(select(AIRPORT_CODE.AIRPORT_ID)
                        .from(AIRPORT_CODE)
                        .where(AIRPORT_CODE.KIND.eq("BTS"), AIRPORT_CODE.CODE.eq(btsCode)))
                .map(Record1::value1);
    }
}
