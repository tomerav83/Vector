package com.vector.airport.data;

import java.util.Map;

/**
 * The Spring event that a new snapshot of the tables serves, after the first build and after each refresh, e.g. for an
 * {@code @EventListener} that caches something computed from them.
 *
 * @param periods each loaded source's period, e.g. {@code faa-register} → {@code 2026-10-29}
 */
public record SnapshotPublished(Map<String, String> periods) {}
