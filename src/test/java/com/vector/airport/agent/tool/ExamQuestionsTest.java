package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.vector.airport.LiveDataTest;
import com.vector.airport.agent.tool.score.Contribution;
import com.vector.airport.agent.tool.score.Kpi;
import com.vector.airport.agent.tool.score.Score;
import com.vector.airport.agent.tool.score.Scores;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The exam's four questions, answered by the tools on the real data, against figures checked by hand on the newest 12
 * months. Runs on demand with the live data tests: the window moves with every month BTS publishes.
 */
@LiveDataTest
class ExamQuestionsTest {

    @Autowired
    Airports airports;

    @Autowired
    Scores scores;

    @Autowired
    Congestion congestion;

    @Autowired
    Traffic traffic;

    @Autowired
    Runways runways;

    @Test
    void newEnglandCandidatesAreRankedAgainstEachOther() {
        List<String> codes = airports.find(null, List.of("MA", "CT", "RI", "NH", "ME", "VT"), null).rows().stream()
                .map(row -> String.valueOf(row.get("airport_id")))
                .toList();

        List<Score> ranked = scores.rank(null, codes, null).rows();

        assertThat(ranked).extracting(Score::airportId).contains("BOS", "BDL", "PVD", "PWM");
        assertThat(ranked).extracting(Score::peers).containsOnly(ranked.size());
    }

    @Test
    void laxAndSantaAnaAreDelayedLessOftenThanTheUs() {
        List<Map<String, Object>> rows =
                congestion.congestion(List.of("LAX", "SNA")).rows();

        assertThat(share(rows.get(0), "delayed_share")).as("LAX").isCloseTo(0.20, within(0.03));
        assertThat(share(rows.get(1), "delayed_share")).as("SNA").isCloseTo(0.19, within(0.03));
        assertThat(rows)
                .allSatisfy(row -> assertThat(share(row, "delayed_share"))
                        .as("%s", row.get("airport_id"))
                        .isLessThan(share(row, "us_delayed_share")));
    }

    @Test
    void aThirdOfAnchorageFlightsAreLongHaulMostlyCargo() {
        Map<String, Object> anc = traffic.longHaul(List.of("ANC"), null).rows().getFirst();

        assertThat(share(anc, "long_haul_share")).isCloseTo(0.30, within(0.03));
        assertThat(share(anc, "passenger_long_haul_share")).isCloseTo(0.03, within(0.02));
        assertThat(share(anc, "cargo_long_haul_share")).isCloseTo(0.52, within(0.04));
    }

    @Test
    void sfoIsFullAndHeldBackByItsAirfield() {
        Map<String, Object> flights = traffic.traffic(List.of("SFO")).rows().getFirst();
        Map<String, Object> delays =
                congestion.congestion(List.of("SFO")).rows().getFirst();
        Score sfo = scores.rank(List.of("SFO"), null, null).rows().getFirst();

        assertThat(share(flights, "load_factor")).isCloseTo(0.82, within(0.03));
        assertThat(share(delays, "nas_share")).as("NAS share of delay minutes").isCloseTo(0.48, within(0.05));
        assertThat(sfo.contributions())
                .filteredOn(contribution -> contribution.kpi() == Kpi.HEADROOM)
                .extracting(Contribution::percentile)
                .as("among the least headroom of the US")
                .allSatisfy(percentile -> assertThat(percentile).isLessThan(0.1));
        assertThat(runways.runways(List.of("SFO")).rows()).hasSize(4);
    }

    private static double share(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).doubleValue();
    }
}
