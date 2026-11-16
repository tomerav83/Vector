package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A code the analyst types, FAA, ICAO or airline, gives its airport's id in any case and any locale. */
@FixtureDataTest
class AirportCodesTest {

    @Autowired
    AirportCodes codes;

    @Test
    void eachCodeGivesItsAirportOnceInTheOrderTyped() {
        assertThat(codes.ids(List.of("KSFO", "AZA", "BOS", "SFO"))).containsExactly("SFO", "IWA", "BOS");
    }

    @Test
    void caseAndSpacesDoNotMatterInAnyLocale() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(codes.ids(List.of(" iwa", "sfo ")))
                    .as("in Turkish, i upper-cases to a dotted capital I")
                    .containsExactly("IWA", "SFO");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void codeNoAirportHasFailsTellingTheModelToLookItUp() {
        assertThatThrownBy(() -> codes.ids(List.of("SFO", "LA")))
                .hasMessage("No airport has the code [LA]; look it up with find");
    }

    /** A code is a bind value, so a quote in it cannot end the SQL string (OWASP: parameterized queries). */
    @Test
    void codeThatLooksLikeSqlIsAnUnknownCode() {
        assertThatThrownBy(() -> codes.ids(List.of("BOS'); drop table airport; --")))
                .hasMessage("No airport has the code [BOS'); DROP TABLE AIRPORT; --]; look it up with find");
    }
}
