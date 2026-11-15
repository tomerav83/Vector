package com.vector.airport.data;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * The {@code data} entry of {@code /actuator/health}: DOWN while a source is left out, naming each and why, e.g.
 * {@code faa-register: java.net.UnknownHostException: nfdc.faa.gov}.
 */
@Component
class DataHealthIndicator implements HealthIndicator {

    private final DSLContext db;

    DataHealthIndicator(DSLContext db) {
        this.db = db;
    }

    @Override
    public Health health() {
        List<Map<String, Object>> leftOut = db.fetch(
                        "select name, problem from source where period is null order by name")
                .intoMaps();
        return (leftOut.isEmpty() ? Health.up() : Health.down())
                .withDetail("leftOut", leftOut)
                .build();
    }
}
