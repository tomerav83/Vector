package com.vector.airport.data;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The committed record of exactly which data the app loads, like npm's {@code package-lock.json}: a country's
 * {@code application-<cc>.yml} says what to load, the lock says which files of each source and the fingerprint of each.
 * Written by the app (by a refresh, or for a source it has no entry for yet), never by hand; review its diff, then
 * commit it.
 */
record Lock(Map<String, LockedSource> sources) {

    /** Records only, so a property is a record component, never a method like {@code Form.isEmpty()}. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withIsGetterVisibility(Visibility.NONE))
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Sorted, so its diffs stay readable. */
    Lock {
        sources = new TreeMap<>(Objects.requireNonNullElse(sources, Map.of()));
    }

    /** The lock in {@code file}, or an empty one before the first start; a malformed file fails. */
    static Lock read(Path file) {
        return Files.exists(file) ? JSON.readValue(file.toFile(), Lock.class) : new Lock(Map.of());
    }

    Optional<LockedSource> find(String source) {
        return Optional.ofNullable(sources.get(source));
    }

    /** This lock with {@code entries} added, replacing those of the same sources. */
    Lock with(Map<String, LockedSource> entries) {
        Map<String, LockedSource> merged = new TreeMap<>(sources);
        merged.putAll(entries);
        return new Lock(merged);
    }

    void write(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON.writeValueAsString(this) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
