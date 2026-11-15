package com.vector.airport.data;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

/**
 * The {@code data} actuator endpoint on the local management port: {@code GET /actuator/data} lists what each source
 * loaded, {@code POST /actuator/data} refreshes them through {@link Snapshot}.
 */
@Component
@Endpoint(id = "data")
class DataEndpoint {

    private final Snapshot snapshot;
    private final DSLContext db;

    DataEndpoint(Snapshot snapshot, DSLContext db) {
        this.snapshot = snapshot;
        this.db = db;
    }

    /** What each source loaded, or why not: the {@code source} table. */
    @ReadOperation
    public List<Map<String, Object>> sources() {
        return db.fetch("select * from source order by name").intoMaps();
    }

    /** Moves every source to its newest period, then lists what each loaded; the current data serves meanwhile. */
    @WriteOperation
    public List<Map<String, Object>> refresh() {
        snapshot.refresh();
        return sources();
    }
}
