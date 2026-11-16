package com.vector.airport.agent.grounding;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The numbers an answer may state: those of its tool results, its question and the fixed wording the model reads (the
 * system prompt, the tool descriptions), e.g. 0.8095 for "81%" or 168,319. A figure of the answer that matches none of
 * them, within its rounding, is stray, e.g. a seat count no tool returned; a year is never stray.
 *
 * @param numbers every number of those texts
 */
record Grounding(List<Double> numbers) {

    /** A number as the texts write it, e.g. 0.8095, 168,319 or 1.0E-4. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?(?:[eE][-+]?\\d+)?");

    /** A figure as an answer writes it, e.g. 81%, 5.3M, 2,117,401, 543k; not the 2 of CY2025. */
    private static final Pattern FIGURE =
            Pattern.compile("(?<![\\w.])\\d[\\d,]*(?:\\.\\d+)?(?:\\s*(?:%|million\\b|billion\\b|[MBk]\\b))?");

    private static final Pattern YEAR = Pattern.compile("\\b(?:19|20)\\d\\d\\b");

    /** What a figure's suffix multiplies it by; a percent is matched against a share. */
    private static final Map<String, Double> SCALES =
            Map.of("%", 1.0, "k", 1e3, "M", 1e6, "million", 1e6, "B", 1e9, "billion", 1e9);

    private static final String BOLD = "**";

    /** A line's sentences: each ends with ., ! or ? and a space. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    Grounding {
        numbers = List.copyOf(numbers);
    }

    /** The numbers of {@code texts}, e.g. a tool's JSON and the question. */
    static Grounding create(Stream<String> texts) {
        return new Grounding(texts.flatMap(text -> NUMBER.matcher(text).results())
                .map(number -> Double.parseDouble(number.group().replace(",", "")))
                .toList());
    }

    /** The figures of {@code answer} that match no number, as written, e.g. [3,348,342, 82%]. */
    List<String> stray(String answer) {
        return FIGURE.matcher(YEAR.matcher(answer).replaceAll(""))
                .results()
                .map(MatchResult::group)
                .map(String::strip)
                .filter(not(this::matches))
                .toList();
    }

    /**
     * {@code answer} without the sentences that hold a stray figure, keeping its lines, "- " bullets and a line's bold.
     */
    String cut(String answer) {
        return answer.lines().map(this::cutLine).filter(not(String::isBlank)).collect(joining("\n"));
    }

    /**
     * Whether a number is {@code figure} within its rounding: 81% is 0.8095, 5.3M is 5,318,211, 3.5% is 0.0352; a share
     * may also be written as a plain percent number, e.g. the 71 of "71st".
     */
    private boolean matches(String figure) {
        String unit =
                SCALES.keySet().stream().filter(figure::endsWith).findFirst().orElse("");
        String digits =
                figure.substring(0, figure.length() - unit.length()).strip().replace(",", "");
        double scale = SCALES.getOrDefault(unit, 1.0);
        double target = Double.parseDouble(digits) * scale;
        int decimals = digits.contains(".") ? digits.length() - digits.indexOf('.') - 1 : 0;
        double tolerance = 0.5 * Math.pow(10, -decimals) * scale;
        return numbers.stream()
                .flatMap(number -> unit.equals("%") || number < 1 ? Stream.of(number, number * 100) : Stream.of(number))
                .anyMatch(number -> Math.abs(number - target) <= tolerance + 1e-9);
    }

    private String cutLine(String line) {
        String bullet = line.startsWith("- ") ? "- " : "";
        String body = line.substring(bullet.length());
        boolean bold = body.length() > 2 * BOLD.length() && body.startsWith(BOLD) && body.endsWith(BOLD);
        String inner = bold ? body.substring(BOLD.length(), body.length() - BOLD.length()) : body;
        String kept = Arrays.stream(SENTENCE_END.split(inner))
                .filter(sentence -> stray(sentence).isEmpty())
                .collect(joining(" "));
        return kept.isBlank() ? "" : bullet + (bold ? BOLD + kept + BOLD : kept);
    }
}
