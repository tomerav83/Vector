package com.vector.airport.data.cache;

import com.vector.airport.data.lock.LockedFile;
import com.vector.airport.data.lock.LockedSource;
import java.util.List;

/**
 * A source's files, fetched: each on local disk, oldest first.
 *
 * @param period the newest release among them, e.g. {@code 2026-01-01}
 */
public record Version(String period, List<Fetched> files) {

    /** Its entry in the lock: the period, and each file with its fingerprint. */
    public LockedSource locked() {
        return new LockedSource(
                period,
                files.stream()
                        .map(file -> new LockedFile(file.file(), file.fingerprint()))
                        .toList());
    }
}
