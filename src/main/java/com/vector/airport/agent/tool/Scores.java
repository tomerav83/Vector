package com.vector.airport.agent.tool;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The {@code rank} tool: scores the airports of the {@code candidate} view among the US airports of their hub size
 * ({@link Peers}) under the analyst's weights ({@link Weighting}), and answers with each score's parts, the state of
 * the KPIs' sources and what a score cannot say.
 */
@Component
class Scores implements ToolCallbackProvider {

    private final DSLContext db;
    private final AirportCodes codes;

    Scores(DSLContext db, AirportCodes codes) {
        this.db = db;
        this.codes = codes;
    }

    @Tool(description = """
            Ranks airports as terminal-expansion candidates with a deterministic score from 0 to 100: each KPI's value \
            becomes a percentile among the US airports of the same FAA hub size, and the score is the weighted sum. Per \
            airport: the score, its rank among its peers and how far that rank moves as the weights vary, and per KPI \
            the value, percentile, share of the score in percent and points. Scores compare airports of one hub size; \
            nonprimary airports, under 10,000 boardings a year, are not scored.""")
    Evidence<Score> rank(
            @ToolParam(description = AirportCodes.DESCRIPTION) List<String> airports,
            @ToolParam(
                            required = false,
                            description =
                                    "KPI weights to change, e.g. [{kpi: GROWTH, weight: 50}]; the others keep theirs")
                    List<Weight> weights) {
        List<String> ids = codes.ids(airports);
        Weighting weighting = Weighting.create(Objects.requireNonNullElse(weights, List.of()));
        List<Score> scores = peers().stream()
                .flatMap(peers -> peers.scores(weighting).stream())
                .filter(score -> ids.contains(score.airportId()))
                .sorted(comparing(Score::hub).thenComparingInt(Score::rank).thenComparing(Score::airportId))
                .toList();
        return Evidence.create(db, scores, caveats(ids, scores), sources());
    }

    /** The candidates of each hub size. */
    private Collection<Peers> peers() {
        return db.fetch("select * from candidate").stream()
                .map(Scores::candidate)
                .collect(groupingBy(Candidate::hub, collectingAndThen(toList(), Peers::new)))
                .values();
    }

    private static Candidate candidate(Record row) {
        return new Candidate(
                row.get("airport_id", String.class),
                row.get("hub", Hub.class),
                Arrays.stream(Kpi.values())
                        .filter(kpi -> row.get(kpi.column()) != null)
                        .collect(toMap(identity(), kpi -> row.get(kpi.column(), Double.class))));
    }

    private static List<String> caveats(List<String> ids, List<Score> scores) {
        Set<String> ranked = scores.stream().map(Score::airportId).collect(toSet());
        Stream<String> meanings = Arrays.stream(Kpi.values()).map(kpi -> "%s is %s.".formatted(kpi, kpi.meaning));
        Stream<String> limits = Stream.of(
                "A score ranks an airport among the US airports of its hub size; scores of different hub sizes do not"
                        + " compare.",
                "A KPI without a value (fare premiums cover the lower 48 states only) is left out, which counts it at"
                        + " the airport's average.",
                "The FAA forecast ignores capacity limits: it is the demand, not what the airport can serve.",
                "The score weighs demand and room to grow, not costs, airport finances or projects already under way.");
        Stream<String> unranked = ids.stream()
                .filter(not(ranked::contains))
                .map(id -> id + " is not ranked: only primary airports with a value for a weighted KPI are.");
        return Stream.of(meanings, limits, unranked).flatMap(identity()).toList();
    }

    /** The sources of the KPIs, and faa-boardings, which gives each airport its hub size. */
    private static String[] sources() {
        return Stream.concat(
                        Stream.of("faa-boardings"), Arrays.stream(Kpi.values()).map(kpi -> kpi.source))
                .distinct()
                .toArray(String[]::new);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }
}
