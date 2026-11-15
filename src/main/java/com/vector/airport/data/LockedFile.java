package com.vector.airport.data;

import com.vector.airport.data.source.SourceFile;

/**
 * One file in the {@link Lock}, with the fingerprint of its data.
 *
 * @param file where and how it downloads, its placeholders filled
 * @param fingerprint of its data, e.g. a SHA-256 hex
 */
record LockedFile(SourceFile file, String fingerprint) {}
