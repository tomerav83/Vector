package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Month;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** A page's releases are the dates its regex finds, newest first; a pattern without a day means the first. */
class LivecheckTest {

    @Test
    void readsTheFullDateOfFaasProductReleaseApi() {
        Livecheck apra = livecheck("<editionDate>(.+?)</editionDate>", "MM/dd/yyyy");

        assertThat(apra.releases("<edition><editionDate>10/29/2026</editionDate><editionNumber>11</editionNumber>"))
                .containsExactly(LocalDate.of(2026, Month.OCTOBER, 29));
    }

    @Test
    void yearsAreTheirFirstDayNewestFirst() {
        Livecheck taf = livecheck("APO100_TAF_Final_(\\d{4})\\.zip", "yyyy");

        assertThat(taf.releases("<a href=APO100_TAF_Final_2024.zip><a href=APO100_TAF_Final_2025.zip>"))
                .containsExactly(LocalDate.of(2025, Month.JANUARY, 1), LocalDate.of(2024, Month.JANUARY, 1));
    }

    @Test
    void releaseNamedTwiceCountsOnce() {
        Livecheck delays = livecheck(">[A-Z][a-z]+, (\\d{4})<", "yyyy");

        assertThat(delays.releases("<OPTION>August, 2026<OPTION>July, 2026<OPTION>December, 2025<"))
                .containsExactly(LocalDate.of(2026, Month.JANUARY, 1), LocalDate.of(2025, Month.JANUARY, 1));
    }

    @Test
    void monthNamesAreEnglish() {
        Livecheck delays = livecheck(">([A-Z][a-z]+, \\d{4})<", "MMMM, yyyy");

        assertThat(delays.releases("<OPTION SELECTED VALUE ='24320'>August, 2026<OPTION VALUE ='24319'>July, 2026<"))
                .containsExactly(LocalDate.of(2026, Month.AUGUST, 1), LocalDate.of(2026, Month.JULY, 1));
    }

    @Test
    void pageWithoutAReleaseFailsNamingThePage() {
        assertThatThrownBy(() -> livecheck("stats-(\\d{4})", "yyyy").releases("<html>moved</html>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("https://example.org/ names no release matching stats-(\\d{4})");
    }

    private static Livecheck livecheck(String regex, String pattern) {
        return new Livecheck("https://example.org/", Pattern.compile(regex), pattern);
    }
}
