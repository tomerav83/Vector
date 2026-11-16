package com.vector.airport.agent.tool.score;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The airports a score ranks among each other, e.g. the 16 of New England, or every US airport with a score: each KPI
 * value becomes a percentile among the airports with a value for it, and the score is the weighted sum (OECD/JRC
 * handbook).
 */
record Peers(List<Candidate> airports) {

    Peers {
        airports = List.copyOf(airports);
    }

    /**
     * Each airport's score under {@code weighting}, its rank, and its best and worst rank as the weights vary; an
     * airport without a value for any weighted KPI has no score.
     */
    List<Score> scores(Weighting weighting) {
        Map<String, Integer> ranks = ranks(weighting);
        List<Map<String, Integer>> variations =
                weighting.variations().map(this::ranks).toList();
        return airports.stream()
                .filter(airport -> ranks.containsKey(airport.airportId()))
                .map(airport -> score(airport, weighting, ranks, variations))
                .toList();
    }

    /** The rank of each airport with a score under {@code weighting}, by airport id. */
    private Map<String, Integer> ranks(Weighting weighting) {
        Map<String, Double> scores = airports.stream()
                .flatMap(airport -> contributions(airport, weighting).stream()
                        .map(contributions -> Map.entry(airport.airportId(), total(contributions))))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        return scores.keySet().stream().collect(toMap(identity(), id -> rank(scores.get(id), scores.values())));
    }

    /** Each KPI's part in the airport's score; empty when no KPI it has a value for has a weight. */
    private Optional<List<Contribution>> contributions(Candidate airport, Weighting weighting) {
        return weighting
                .shares(airport)
                .map(shares -> Arrays.stream(Kpi.values())
                        .map(kpi -> airport.value(kpi)
                                .map(value -> contribution(kpi, value, shares.get(kpi)))
                                .orElse(new Contribution(
                                        kpi, null, null, values(kpi).size(), null, 0, 0)))
                        .toList());
    }

    /**
     * Its rank among the peers with a value for {@code kpi}, 1 plus those that beat it, and the share of them it beats,
     * a tie counting half.
     */
    private Contribution contribution(Kpi kpi, double value, double share) {
        List<Double> peers = values(kpi);
        long below = peers.stream().filter(peer -> peer < value).count();
        long above = peers.stream().filter(peer -> peer > value).count();
        double percentile = (below + (peers.size() - below - above) / 2.0) / peers.size();
        double beaten = kpi.higherIsBetter ? percentile : 1 - percentile;
        int rank = 1 + (int) (kpi.higherIsBetter ? above : below);
        return new Contribution(kpi, value, rank, peers.size(), beaten, share, share * beaten);
    }

    /** The values the peers have for {@code kpi}. */
    private List<Double> values(Kpi kpi) {
        return airports.stream().flatMap(peer -> peer.value(kpi).stream()).toList();
    }

    private static double total(List<Contribution> contributions) {
        return contributions.stream().mapToDouble(Contribution::points).sum();
    }

    /** 1 plus the number of {@code scores} above {@code score}, so a tie shares a place: "sport" ranking, 1-2-2-4. */
    private static int rank(double score, Collection<Double> scores) {
        return 1 + (int) scores.stream().filter(other -> other > score).count();
    }

    private Score score(
            Candidate airport, Weighting weighting, Map<String, Integer> ranks, List<Map<String, Integer>> variations) {
        String id = airport.airportId();
        IntSummaryStatistics range = Stream.concat(Stream.of(ranks), variations.stream())
                .flatMap(variation -> Optional.ofNullable(variation.get(id)).stream())
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        List<Contribution> contributions = contributions(airport, weighting).orElseThrow();
        return new Score(
                id,
                airport.name(),
                airport.hub(),
                airport.boardings(),
                total(contributions),
                ranks.get(id),
                ranks.size(),
                range.getMin(),
                range.getMax(),
                contributions);
    }
}
