package com.vector.airport.agent.tool.score;

import static com.vector.airport.view.tables.Candidate.CANDIDATE;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.jooq.impl.DSL.noCondition;

import com.vector.airport.agent.tool.AirportCodes;
import com.vector.airport.agent.tool.Evidence;
import com.vector.airport.agent.tool.Hub;
import java.util.Arrays;
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
 * The {@code rank} tool: scores airports of the {@code candidate} view among the airports the question is about
 * ({@link Peers}), e.g. New England's, under the analyst's weights ({@link Weighting}), and answers with each score's
 * parts, the state of the KPIs' sources and what a score cannot say.
 */
@Component
public class Scores implements ToolCallbackProvider {

    /** How many airports a ranking of every US airport shows when none are named. */
    private static final int TOP = 10;

    private final DSLContext db;
    private final AirportCodes codes;

    Scores(DSLContext db, AirportCodes codes) {
        this.db = db;
        this.codes = codes;
    }

    @Tool(description = """
            Ranks airports as terminal-expansion candidates with a deterministic score from 0 to 100 among the \
            airports the question is about: each KPI's value becomes a percentile among them, and the score is the \
            weighted sum. Per airport: its name, boardings, score, rank and how far that rank moves as the weights \
            vary, and per KPI the value, its rank among them, percentile, share of the score in percent and points. \
            Nonprimary airports, under 10,000 boardings a year, are not scored.""")
    public Evidence<Score> rank(
            @ToolParam(
                            required = false,
                            description = "the airports to show, e.g. [BOS]; none: every airport of among, or the top "
                                    + TOP + " of the US")
                    List<String> airports,
            @ToolParam(
                            required = false,
                            description = "the airports to rank against each other: those the question is about, the"
                                    + " named ones (PVD or BOS?) or a place's from find (New England, the New York"
                                    + " area); none: every US airport with a score, e.g. for one airport alone")
                    List<String> among,
            @ToolParam(
                            required = false,
                            description =
                                    "KPI weights to change, e.g. [{kpi: GROWTH, weight: 50}]; the others keep theirs")
                    List<Weight> weights) {
        return db.transactionResult(_ -> {
            List<String> shown = codes.ids(Objects.requireNonNullElse(airports, List.of()));
            List<String> pool = codes.ids(Objects.requireNonNullElse(among, List.of()));
            Weighting weighting = Weighting.create(Objects.requireNonNullElse(weights, List.of()));
            List<Score> scores = peers(pool, shown).scores(weighting).stream()
                    .sorted(comparingInt(Score::rank).thenComparing(Score::airportId))
                    .toList();
            List<Score> rows = shown.isEmpty()
                    ? scores.stream()
                            .limit(pool.isEmpty() ? TOP : scores.size())
                            .toList()
                    : scores.stream()
                            .filter(score -> shown.contains(score.airportId()))
                            .toList();
            List<String> asked =
                    Stream.concat(shown.stream(), pool.stream()).distinct().toList();
            return Evidence.create(db, rows, definitions(), limitations(asked, scores), sources());
        });
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return ToolCallbacks.from(this);
    }

    /** The candidates of {@code pool} and {@code shown}; every candidate when the pool is empty. */
    private Peers peers(List<String> pool, List<String> shown) {
        return new Peers(db
                .selectFrom(CANDIDATE)
                .where(
                        pool.isEmpty()
                                ? noCondition()
                                : CANDIDATE.AIRPORT_ID.in(pool).or(CANDIDATE.AIRPORT_ID.in(shown)))
                .stream()
                .map(Scores::candidate)
                .toList());
    }

    private static Candidate candidate(Record row) {
        return new Candidate(
                row.get(CANDIDATE.AIRPORT_ID),
                row.get(CANDIDATE.NAME),
                row.get(CANDIDATE.HUB, Hub.class),
                row.get(CANDIDATE.BOARDINGS),
                Arrays.stream(Kpi.values())
                        .filter(kpi -> row.get(kpi.column()) != null)
                        .collect(toMap(identity(), kpi -> row.get(kpi.column(), Double.class))));
    }

    /** What each KPI is. */
    private static List<String> definitions() {
        return Arrays.stream(Kpi.values())
                .map(kpi -> "%s is %s.".formatted(kpi, kpi.meaning))
                .toList();
    }

    /** What a score cannot say, and which of {@code ids} it does not rank. */
    private static List<String> limitations(List<String> ids, List<Score> scores) {
        Set<String> ranked = scores.stream().map(Score::airportId).collect(toSet());
        List<String> unranked = ids.stream().filter(not(ranked::contains)).toList();
        Stream<String> left = unranked.isEmpty()
                ? Stream.empty()
                : Stream.of("Not ranked, as only primary airports with a value for a weighted KPI are: %s."
                        .formatted(String.join(", ", unranked)));
        Stream<String> limits = Stream.of(
                "A score and its ranks hold only among the airports ranked together here; among other airports they"
                        + " differ.",
                "A KPI without a value (fare premiums cover the lower 48 states only) is left out, which counts it at"
                        + " the airport's average.",
                "The FAA forecast is not constrained by capacity: it projects demand, not what the airport can serve.",
                "The score weighs demand and room to grow; it does not consider costs, airport finances or projects"
                        + " already under way.");
        return Stream.concat(limits, left).toList();
    }

    /** The sources of the KPIs, and faa-boardings, which gives each airport its hub size and boardings. */
    private static String[] sources() {
        return Stream.concat(
                        Stream.of("faa-boardings"), Arrays.stream(Kpi.values()).map(kpi -> kpi.source))
                .distinct()
                .toArray(String[]::new);
    }
}
