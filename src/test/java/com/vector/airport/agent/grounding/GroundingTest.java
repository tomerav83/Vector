package com.vector.airport.agent.grounding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** An answer's figure is grounded when a number of its tool results matches it within its rounding; else it is cut. */
class GroundingTest {

    @ParameterizedTest(name = "{0} against {1}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
            81%       | 0.8095  | true  | a share as a rounded percent
            82%       | 0.8095  | false | a share the model rounded wrong (answer 43)
            3.5%      | 0.0352  | true  | a share with one decimal
            5.3M      | 5318211 | true  | millions with one decimal
            543k      | 543020  | true  | thousands
            2,117,401 | 2117401 | true  | a count with commas
            162,319   | 162937  | false | a count the model mistyped (answer 26b)
            71st      | 0.7059  | true  | a percentile in its 0-100 form
            2025      | 0       | true  | a year
            """)
    void figureIsGroundedByANumberWithinItsRounding(String figure, double number, boolean grounded, String reason) {
        assertThat(new Grounding(List.of(number)).stray("It is " + figure + "."))
                .as(reason)
                .isEqualTo(grounded ? List.of() : List.of(figure));
    }

    @Test
    void numbersComeFromEveryTextAsWritten() {
        Grounding grounding = Grounding.create(
                Stream.of("{\"load_factor\":0.8095,\"seats\":3348342,\"share\":1.0E-4}", "the newest 12 months"));

        assertThat(grounding.stray("3,348,342 seats over 12 months, 81% full, 0.01% cargo"))
                .isEmpty();
    }

    @Test
    void cutDropsTheSentencesWithAStrayFigureAndKeepsTheLinesAndTheirBold() {
        Grounding grounding = new Grounding(List.of(2_533_180.0));

        assertThat(grounding.cut(
                        "**Toronto Pearson is not covered. BUF had 2,533,180 boardings, 82% of seats filled.**\n"
                                + "- BUF flew 3,348,342 seats.\n"
                                + "- I used BUF for Buffalo."))
                .isEqualTo("**Toronto Pearson is not covered.**\n- I used BUF for Buffalo.");
    }
}
