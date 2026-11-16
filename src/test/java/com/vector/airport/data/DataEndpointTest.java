package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.FixtureDataTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;

/**
 * On the fixture data: the data endpoint lists what each source loaded, the health is UP, and an {@code @EventListener}
 * hears the first build. Over HTTP on the management port: {@code AppTest}.
 */
@FixtureDataTest
class DataEndpointTest {

    @Autowired
    DataEndpoint endpoint;

    @Autowired
    DataProperties properties;

    @Autowired
    DataHealthIndicator health;

    @Autowired
    Announcements announcements;

    @Test
    void listsWhatEachSourceLoaded() {
        assertThat(endpoint.sources())
                .extracting(source -> source.get("name"))
                .containsExactlyInAnyOrderElementsOf(properties.sources().keySet());
        assertThat(endpoint.sources()).allSatisfy(source -> assertThat(source).containsEntry("matches_lock", true));
    }

    @Test
    void healthIsUpWhenEverySourceLoaded() {
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void eventListenerHearsTheFirstBuild() {
        assertThat(announcements.heard)
                .singleElement()
                .satisfies(published -> assertThat(published.periods())
                        .containsOnlyKeys(properties.sources().keySet()));
    }
}
