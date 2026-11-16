package com.vector.airport.data.lock;

import java.util.List;

/**
 * One source in the {@link Lock}: what a refresh resolved, so a start fetches it as is.
 *
 * @param period the newest release it loaded, e.g. {@code 2026-01-01}
 * @param files its files, oldest first
 */
public record LockedSource(String period, List<LockedFile> files) {}
