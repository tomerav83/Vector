package com.vector.airport.data.format.impl;

import static java.util.Comparator.comparing;

import com.vector.airport.data.format.Format;
import com.vector.airport.data.format.Formats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

/** A zip, read in place and judged by the files it holds: NASR, TAF and BTS zips. */
@Component
public class Zip implements Format {

    /** Ends an entry's name in the fingerprint, so a name and the start of its content cannot run together. */
    private static final int NAME_END = 0;

    @Override
    public Set<MediaType> types() {
        return Set.of(MediaType.APPLICATION_ZIP);
    }

    /**
     * Each entry has rows by its own format, so a BTS zip of header-only CSVs has none; an entry no format reads, e.g.
     * NASR's PDF readme, does not count.
     */
    @Override
    public boolean hasRows(Path file, Formats formats) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(file)) {
            return entries(zip).stream().noneMatch(entry -> isEmpty(entry, formats));
        }
    }

    /**
     * BTS stamps every zip with the time of the download, so only each entry's name and content count, in name order,
     * as in Go's module hash.
     */
    @Override
    public String fingerprint(Path file) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(file);
                Sha256 digest = new Sha256()) {
            for (Path entry : entries(zip)) {
                digest.write(entry.toString().getBytes(StandardCharsets.UTF_8));
                digest.write(NAME_END);
                Files.copy(entry, digest);
            }
            return digest.hex();
        }
    }

    /** @throws NoSuchFileException naming the entries it holds, e.g. when BTS renames {@code T100.csv} */
    @Override
    public Path extract(Path file, String entry, Path dir) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(file)) {
            Path source = zip.getPath(entry);
            if (Files.notExists(source)) {
                throw new NoSuchFileException(entry, null, "the zip holds only " + entries(zip));
            }
            Path target = Files.createTempFile(dir, null, "-" + source.getFileName());
            return Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Its files in name order, named as in the zip, e.g. {@code data/T100.csv}; read while {@code zip} is open. */
    private static List<Path> entries(FileSystem zip) throws IOException {
        Path root = zip.getPath("/");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .sorted(comparing(Path::toString))
                    .toList();
        }
    }

    /** An entry of a format that says it has no rows. */
    private static boolean isEmpty(Path entry, Formats formats) {
        try {
            Optional<Format> format = formats.find(entry);
            return format.isPresent() && !format.orElseThrow().hasRows(entry, formats);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
