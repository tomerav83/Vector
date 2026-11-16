package com.vector.airport.data;

import static com.vector.airport.view.tables.Airport.AIRPORT;
import static com.vector.airport.view.tables.AirportCode.AIRPORT_CODE;
import static com.vector.airport.view.tables.Boardings.BOARDINGS;
import static com.vector.airport.view.tables.FarePremium.FARE_PREMIUM;
import static com.vector.airport.view.tables.Forecast.FORECAST;
import static com.vector.airport.view.tables.Metro.METRO;
import static com.vector.airport.view.tables.Runway.RUNWAY;
import static com.vector.airport.view.tables.Segment.SEGMENT;
import static com.vector.airport.view.tables.Source.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.jooq.impl.DSL.countDistinct;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

import com.vector.airport.LiveDataTest;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.TableField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Each US source on the real files of its locked period, against facts checked by hand on them; tests are named after
 * their source, as in {@link UsDataTest}. It catches upstream changes: revised or removed files, renamed columns, new
 * codes.
 */
@LiveDataTest
class UsLiveDataTest {

    /** The exam's long-haul threshold. */
    private static final int LONG_HAUL_MILES = 3000;

    @Autowired
    DSLContext db;

    @Test
    void everySourceLoadedTheLockedFiles() {
        assertThat(db.select(SOURCE.NAME, SOURCE.PROBLEM)
                        .from(SOURCE)
                        .where(SOURCE.PERIOD.isNull().or(SOURCE.MATCHES_LOCK.isFalse()))
                        .fetchMaps())
                .isEmpty();
    }

    @Test
    void noCodePointsAtTwoAirports() {
        assertThat(db.select(AIRPORT_CODE.CODE, AIRPORT_CODE.KIND)
                        .from(AIRPORT_CODE)
                        .groupBy(AIRPORT_CODE.CODE, AIRPORT_CODE.KIND)
                        .having(countDistinct(AIRPORT_CODE.AIRPORT_ID).gt(1))
                        .fetchMaps())
                .isEmpty();
    }

    @Test
    void faaRegisterHasTheUsAirportsAndSeaplaneBases() {
        assertThat(db.fetchCount(AIRPORT))
                .as("APT_BASE.csv's US airports (A) and seaplane bases (C), 29 Oct 2026")
                .isEqualTo(13423);
        assertThat(db.fetchCount(AIRPORT, AIRPORT.CLOSED.isFalse()))
                .as("of those, ARPT_STATUS O")
                .isEqualTo(13151);
    }

    @Test
    void faaRegisterKeepsEveryRunway() {
        assertThat(db.fetchCount(RUNWAY, RUNWAY.AIRPORT_ID.eq("DEN"))).isEqualTo(6);
    }

    @Test
    void faaRegisterPlacesEachAirportInItsFaaRegion() {
        assertThat(db.fetchValues(select(AIRPORT.AIRPORT_ID)
                        .from(AIRPORT)
                        .where(AIRPORT.REGION.eq("ANE"), AIRPORT.HUB.isNotNull())))
                .contains("BDL", "BOS", "BTV", "MHT", "PVD", "PWM");
    }

    @ParameterizedTest(name = "{0} shows as {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            BOS | BOS | the same code everywhere
            IWA | AZA | Mesa Gateway: FAA IWA, airlines AZA
            GPI | FCA | Glacier Park: FAA GPI, airlines FCA
            DJT | DJT | was PBI until Aug 2026
            1G4 | 1G4 | Grand Canyon West: statistics use the FAA code, not IATA GCW
            GUM | GUM | a territory: BTS state code TT
            SJU | SJU | San Juan, Puerto Rico
            """)
    void btsAirportsMatchesCodesByPositionNotByCode(String airportId, String iata, String why) {
        assertThat(iata(airportId)).as(why).isEqualTo(iata);
    }

    @Test
    void btsAirportsFindsAnOldCode() {
        assertThat(value(AIRPORT_CODE.AIRPORT_ID, AIRPORT_CODE.KIND.eq("BTS").and(AIRPORT_CODE.CODE.eq("PBI"))))
                .isEqualTo("DJT");
    }

    @Test
    void btsSegmentsGiveAlmostAllUsTrafficAnAirport() {
        assertThat(db.fetchValue(select(sum(SEGMENT.PASSENGERS)
                                .filterWhere(SEGMENT.ORIGIN_ID.isNull())
                                .div(sum(SEGMENT.PASSENGERS))
                                .cast(Double.class))
                        .from(SEGMENT)
                        .where(SEGMENT.ORIGIN_COUNTRY.eq("US"))))
                .isLessThan(0.0001);
    }

    @Test
    void btsSegmentsGiveAnchoragesLongHaulShareOfCy2025Departures() {
        // verified from T-100 by hand: all 31.1%, cargo 52.9%, passenger 2.8%
        assertThat(longHaulShare(noCondition())).isCloseTo(31.1, within(0.3));
        assertThat(longHaulShare(SEGMENT.SERVICE_CLASS.in("SCHEDULED_CARGO", "CHARTER_CARGO")))
                .isCloseTo(52.9, within(0.3));
        assertThat(longHaulShare(SEGMENT.SERVICE_CLASS.in("SCHEDULED_PASSENGER", "CHARTER_PASSENGER")))
                .isCloseTo(2.8, within(0.3));
    }

    @Test
    void faaBoardingsFindEveryReportedAirportInTheRegister() {
        // the CY2025 report lists 516 airports with boardings
        assertThat(db.fetchCount(BOARDINGS, BOARDINGS.YEAR.eq(2025))).isEqualTo(516);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', textBlock = """
            LARGE      | 30  | the CY2025 boardings report's footer: Large Count
            MEDIUM     | 35  | Medium Count
            SMALL      | 79  | Small Count
            NONHUB     | 258 | Nonhub Count
            NONPRIMARY | 114 | Nonprimary Commercial Service
            """)
    void faaBoardingsGiveEachHubSize(String hub, int airports, String reason) {
        assertThat(db.fetchCount(AIRPORT, AIRPORT.HUB.eq(hub))).as(reason).isEqualTo(airports);
    }

    @Test
    void faaBoardingsMatchTheCy2025Report() {
        assertThat(value(BOARDINGS.BOARDINGS_, BOARDINGS.AIRPORT_ID.eq("ATL").and(BOARDINGS.YEAR.eq(2025))))
                .isEqualTo(51_459_786L);
    }

    @Test
    void faaForecastAddsUpTheActualBoardings() {
        // TAF FY2024 actual = air carrier + air taxi + commuter + US-flag and foreign-flag international
        assertThat(value(FORECAST.BOARDINGS, FORECAST.AIRPORT_ID.eq("ATL").and(FORECAST.YEAR.eq(2024))))
                .isEqualTo(52_270_575L);
    }

    @Test
    void faaForecastGroupsEachMetroAreaUnderItsHub() {
        assertThat(metro("LAX")).contains("LAX", "SNA", "BUR", "ONT", "LGB", "PSP");
        assertThat(metro("BOS")).contains("MHT", "ORH").doesNotContain("PVD");
    }

    @Test
    void dotFaresGiveThePremiumOf2026Q1() {
        assertThat(premium("SNA")).isCloseTo(0.081, within(0.001));
        assertThat(premium("LAX")).isCloseTo(0.021, within(0.001));
        assertThat(premium("BTV")).isCloseTo(0.122, within(0.001));
    }

    @Test
    void dotFaresCoverTheLower48Only() {
        assertThat(db.fetchCount(FARE_PREMIUM, FARE_PREMIUM.AIRPORT_ID.eq("ANC")))
                .as("DOT Table 7 covers the lower 48 only")
                .isZero();
    }

    private String iata(String airportId) {
        return value(AIRPORT.IATA, AIRPORT.AIRPORT_ID.eq(airportId));
    }

    /** The value of {@code column} in its table's one row {@code where} keeps. */
    private <T> T value(TableField<?, T> column, Condition where) {
        return db.fetchSingle(select(column).from(column.getTable()).where(where))
                .value1();
    }

    /** The percent of Anchorage's CY2025 departures on {@code flights} that fly at least the exam's long haul. */
    private double longHaulShare(Condition flights) {
        return db.fetchValue(select(inline(100.0)
                        .mul(sum(SEGMENT.DEPARTURES_PERFORMED)
                                .filterWhere(SEGMENT.DISTANCE_MI.ge((double) LONG_HAUL_MILES)))
                        .div(sum(SEGMENT.DEPARTURES_PERFORMED))
                        .cast(Double.class))
                .from(SEGMENT)
                .where(SEGMENT.ORIGIN_ID.eq("ANC"), SEGMENT.YEAR.eq(2025), flights));
    }

    private List<String> metro(String hubId) {
        return db.fetchValues(select(METRO.AIRPORT_ID).from(METRO).where(METRO.HUB_ID.eq(hubId)));
    }

    private double premium(String airportId) {
        return value(FARE_PREMIUM.PREMIUM, FARE_PREMIUM.AIRPORT_ID.eq(airportId));
    }
}
