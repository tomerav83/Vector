package com.vector.airport.data.cache;

import com.vector.airport.data.downloader.Downloaders;
import com.vector.airport.data.format.Format;
import com.vector.airport.data.format.Formats;
import com.vector.airport.data.lock.LockedFile;
import com.vector.airport.data.lock.LockedSource;
import com.vector.airport.data.source.Source;
import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.web.Unpublished;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

/**
 * Local copies of the source files (data/cache, gitignored), one per file key, kept across starts, as Homebrew keeps
 * its downloads by URL. A download waits in a staging folder until its source has loaded, so a failed, rejected or
 * interrupted download never replaces a good copy; closing deletes the staging folder with whatever is left in it.
 */
public final class Cache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Cache.class);

    /** Inside the cache, so keeping a download is an atomic rename. */
    private static final String STAGING = "staging";

    private final Path dir;
    private final Downloaders downloaders;
    private final Formats formats;
    private final boolean frozen;
    private final Path staging;

    /** @param frozen a locked file missing from the cache fails instead of downloading ({@code vector.data.frozen}) */
    public Cache(Path dir, Downloaders downloaders, Formats formats, boolean frozen) {
        this.dir = dir;
        this.downloaders = downloaders;
        this.formats = formats;
        this.frozen = frozen;
        this.staging = dir.resolve(STAGING);
    }

    /** The locked files of a source: each from the cache, else downloaded unless frozen. */
    public Version fetch(LockedSource locked) {
        return new Version(
                locked.period(),
                locked.files().stream().map(LockedFile::file).map(this::fetch).toList());
    }

    /**
     * The files of {@code periods} of {@code source}, oldest first, every one downloaded again, since a file can change
     * upstream under the same key (a BTS year grows by a month at a time).
     */
    public Version download(Source source, List<String> periods) {
        return new Version(
                periods.getLast(),
                periods.stream().map(source::file).map(this::download).toList());
    }

    /**
     * A loaded version's downloads join the cache, replacing the copies they were downloaded again for; one that cannot
     * move is downloaded again next start, since its source has loaded already.
     */
    public void keep(Version version) {
        version.files().stream().filter(file -> file.path().startsWith(staging)).forEach(this::keep);
    }

    @Override
    public void close() {
        FileSystemUtils.deleteRecursively(staging.toFile());
    }

    private Fetched fetch(SourceFile file) {
        Path cached = path(file);
        if (Files.isRegularFile(cached)) {
            return fetched(file, cached);
        }
        Assert.state(!frozen, () -> file.key() + " is not in " + dir + ", and vector.data.frozen never downloads");
        return download(file);
    }

    private Fetched fetched(SourceFile file, Path path) {
        try {
            return new Fetched(file, path, formats.detect(path).fingerprint(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Downloads with the file's downloader into the staging folder.
     *
     * @throws Unpublished for a 404, or a web page or a table without rows where the file should be; a web page is the
     *     file itself when its format is {@value SourceFile#PAGE}
     */
    private Fetched download(SourceFile file) {
        log.info("Downloading {}", file.key());
        try {
            Path staged = Files.createTempFile(Files.createDirectories(staging), null, null);
            downloaders.forName(file.downloader(), file.key()).download(file, staged);
            Format format = formats.detect(staged);
            if (!file.isPage() && !format.hasRows(staged, formats)) {
                throw new Unpublished(file.key() + " holds no data rows");
            }
            return new Fetched(file, staged, format.fingerprint(staged));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot download " + file.key(), e);
        }
    }

    /** A name made from the file's key, which can be longer than a file name may be. */
    private Path path(SourceFile file) {
        return dir.resolve(UUID.nameUUIDFromBytes(file.key().getBytes(StandardCharsets.UTF_8))
                .toString());
    }

    private void keep(Fetched download) {
        try {
            Files.move(download.path(), path(download.file()), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn(
                    "Cannot keep {}; the next start downloads it again: {}",
                    download.file().key(),
                    e.toString());
        }
    }
}
