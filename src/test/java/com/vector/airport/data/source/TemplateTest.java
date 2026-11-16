package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A placeholder is its value in the context, a date formatted by its pattern; an unknown one fails naming the known.
 */
class TemplateTest {

    @Test
    void formatsDatesWithPatterns() {
        Map<String, Object> context = Map.of("date", LocalDate.of(2026, Month.OCTOBER, 29), "year", Year.of(2025));

        assertThat(Template.render("{date:dd_MMM_yyyy}_APT_CSV.zip", context)).isEqualTo("29_Oct_2026_APT_CSV.zip");
        assertThat(Template.render("nasr-{date}.zip", context)).isEqualTo("nasr-2026-10-29.zip");
        assertThat(Template.render("CY {year:yy}", context)).isEqualTo("CY 25");
    }

    @Test
    void delayCauseMonthsUseBtsLetterEncoding() {
        // verified against working download URLs: FHGDM = Sep 2025, FHGFD = Aug 2026
        assertThat(Template.render("{to:bts-month-code}", Map.of("to", YearMonth.of(2025, Month.SEPTEMBER))))
                .isEqualTo("FHGDM");
        assertThat(Template.render("{to:bts-month-code}", Map.of("to", YearMonth.of(2026, Month.AUGUST))))
                .isEqualTo("FHGFD");
    }

    @Test
    void patternThatDoesNotApplyFails() {
        assertThatThrownBy(() -> Template.render("Q{quarter:yyyy}", Map.of("quarter", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pattern 'yyyy' does not apply to 3");
    }

    @Test
    void valueWithDollarOrBackslashIsWrittenAsIs() {
        assertThat(Template.render("q={q}", Map.of("q", "$where\\1"))).isEqualTo("q=$where\\1");
    }

    @Test
    void unknownPlaceholderFailsAndListsTheKnownOnes() {
        assertThatThrownBy(() -> Template.render("t100-{yaer}.zip", Map.of("year", Year.of(2025))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{yaer}")
                .hasMessageContaining("[year]");
    }
}
