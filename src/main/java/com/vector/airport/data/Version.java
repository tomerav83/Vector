package com.vector.airport.data;

import java.util.List;

/**
 * A source's files, fetched: each on local disk, oldest first.
 *
 * @param period the newest release among them, e.g. {@code 2026-01-01}
 */
record Version(String period, List<Fetched> files) {

    /** Its entry in the lock: the period, and each file with its fingerprint. */
    LockedSource locked() {
        return new LockedSource(
                period,
                files.stream()
                        .map(file -> new LockedFile(file.file(), file.fingerprint()))
                        .toList());
    }
}
