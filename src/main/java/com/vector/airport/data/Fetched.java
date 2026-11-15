package com.vector.airport.data;

import com.vector.airport.data.source.SourceFile;
import java.nio.file.Path;

/** A {@link SourceFile} on local disk, with the fingerprint of its data. */
record Fetched(SourceFile file, Path path, String fingerprint) {}
