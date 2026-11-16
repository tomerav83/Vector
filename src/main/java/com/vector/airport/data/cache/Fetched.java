package com.vector.airport.data.cache;

import com.vector.airport.data.source.SourceFile;
import java.nio.file.Path;

/** A {@link SourceFile} on local disk, with the fingerprint of its data. */
public record Fetched(SourceFile file, Path path, String fingerprint) {}
