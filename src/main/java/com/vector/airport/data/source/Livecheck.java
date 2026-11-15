package com.vector.airport.data.source;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.util.Comparator.reverseOrder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Where a source names its releases, as Homebrew's livecheck does: a page, a regex whose group is a release's date on
 * it, and that date's {@link DateTimeFormatter} pattern, e.g. FAA's {@code <editionDate>10/29/2026</editionDate>} and
 * {@code MM/dd/yyyy}; a pattern without a month or a day means the first, so {@code yyyy} is January 1.
 *
 * @param url the page that names the releases
 * @param regex finds each release's date on the page, in its first group
 * @param pattern the date's pattern, built once, e.g. {@code MMMM, yyyy} for {@code August, 2026}
 */
public record Livecheck(String url, Pattern regex, DateTimeFormatter pattern) {

    /** As a profile declares it, under {@code livecheck}. */
    @ConstructorBinding
    public Livecheck(String url, Pattern regex, String pattern) {
        this(url, regex, pattern(pattern));
    }

    /**
     * The newest release its page names and {@code history} before it, oldest first, e.g. {@code [2025-01-01,
     * 2026-01-01]}.
     *
     * @throws IllegalStateException when the page names none, e.g. its layout changed
     */
    List<String> periods(Web web, int history) {
        return releases(web.read(url)).stream()
                .limit(history + 1)
                .map(LocalDate::toString)
                .toList()
                .reversed();
    }

    /** Every release {@code page} names, newest first, each once. */
    List<LocalDate> releases(String page) {
        List<LocalDate> releases = regex.matcher(page)
                .results()
                .map(release -> LocalDate.parse(release.group(1), pattern))
                .distinct()
                .sorted(reverseOrder())
                .toList();
        if (releases.isEmpty()) {
            throw new IllegalStateException("%s names no release matching %s".formatted(url, regex));
        }
        return releases;
    }

    private static DateTimeFormatter pattern(String pattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .parseDefaulting(MONTH_OF_YEAR, 1)
                .parseDefaulting(DAY_OF_MONTH, 1)
                .toFormatter(Locale.ENGLISH);
    }
}
