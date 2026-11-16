package com.vector.airport.data.source;

import static java.util.stream.Collectors.joining;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@code {name}} and {@code {name:pattern}} placeholders, as Python's {@code str.format} does: a date-like value
 * is written with its pattern, a {@link DateTimeFormatter} pattern ({@code {date:dd_MMM_yyyy}} → {@code 29_Oct_2026})
 * or {@link #BTS_MONTH_CODE}. An unknown placeholder fails, so a typo never reaches a URL or a query.
 */
public final class Template {

    /**
     * BTS's delay-cause download names a month by its running number ({@code year * 12 + month}) written with the
     * letters D-M for the digits 0-9: Sep 2025 = 24309 = {@code FHGDM}.
     */
    static final String BTS_MONTH_CODE = "bts-month-code";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)(?::([^}]+))?}");
    private static final char BTS_ZERO = 'D';

    private Template() {}

    /** Fills placeholders from {@code context}, e.g. {@code {year}} with its {@code year}. */
    static String render(String template, Map<String, Object> context) {
        return render(template, (name, pattern) -> format(value(context, name, template), pattern));
    }

    /**
     * Fills placeholders with what {@code resolver} returns for each name and the text after its colon, e.g. the zip
     * entry of an SQL file's {@code {file:T100.csv}}.
     */
    public static String render(String template, BiFunction<String, Optional<String>, String> resolver) {
        return PLACEHOLDER
                .matcher(template)
                .replaceAll(placeholder -> Matcher.quoteReplacement(
                        resolver.apply(placeholder.group(1), Optional.ofNullable(placeholder.group(2)))));
    }

    private static Object value(Map<String, Object> context, String name, String template) {
        return Optional.ofNullable(context.get(name))
                .orElseThrow(() -> new IllegalArgumentException("Unknown placeholder {%s} in '%s'; available: %s"
                        .formatted(name, template, new TreeSet<>(context.keySet()))));
    }

    private static String format(Object value, Optional<String> pattern) {
        return pattern.map(given -> format(value, given)).orElseGet(value::toString);
    }

    private static String format(Object value, String pattern) {
        return switch (value) {
            case YearMonth month when pattern.equals(BTS_MONTH_CODE) -> btsMonthCode(month);
            case TemporalAccessor temporal ->
                DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(temporal);
            default ->
                throw new IllegalArgumentException("Pattern '%s' does not apply to %s".formatted(pattern, value));
        };
    }

    /** PROLEPTIC_MONTH counts from January of year 0, so it is the running month number minus one. */
    private static String btsMonthCode(YearMonth month) {
        return Long.toString(month.getLong(ChronoField.PROLEPTIC_MONTH) + 1)
                .chars()
                .mapToObj(digit -> Character.toString(BTS_ZERO + (digit - '0')))
                .collect(joining());
    }
}
