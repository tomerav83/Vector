package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Spring Boot binds the sources: a country is a profile that adds its own, the US is the default, and a mistake or a
 * start without a country fails naming it.
 */
class DataPropertiesTest {

    private final ApplicationContextRunner app = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DataConfiguration.class);

    private final ApplicationContextRunner countries =
            app.withPropertyValues("spring.config.location=classpath:data-properties-test/");

    @Test
    void theUsIsTheDefaultCountry() {
        app.run(context -> assertThat(context.getBean(DataProperties.class).sources())
                .containsOnlyKeys(
                        "faa-register",
                        "bts-airports",
                        "faa-boardings",
                        "faa-forecast",
                        "bts-segments",
                        "bts-delays",
                        "dot-fares"));
    }

    @Test
    void eachCountryIsAProfileThatAddsItsSources() {
        countries
                .withPropertyValues("spring.profiles.active=xx,yy")
                .run(context -> assertThat(context.getBean(DataProperties.class).sources())
                        .containsOnlyKeys("xx-register", "xx-traffic", "yy-traffic"));
    }

    @Test
    void startWithoutACountryFailsSayingHowToPickOne() {
        countries.run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("start with a country's profile"));
    }

    @Test
    void misspelledKeyFailsTheStartNamingIt() {
        countries
                .withPropertyValues("spring.profiles.active=typo")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("vector.data.sources.stats.perod"));
    }

    @Test
    void unknownPlaceholderFailsTheStartNamingItsSource() {
        countries
                .withPropertyValues("spring.profiles.active=placeholder")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining("vector.data.sources.stats")
                        .rootCause()
                        .hasMessageContaining("Unknown placeholder {yaer}"));
    }

    @Test
    void misspelledFormKeyFailsTheStartNamingIt() {
        countries
                .withPropertyValues("spring.profiles.active=form-typo")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("vector.data.sources.stats.form.chekced"));
    }
}
