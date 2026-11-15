package com.vector.airport.data;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.source.Downloaders;
import com.vector.airport.data.source.Source;
import com.vector.airport.data.source.Web;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * The data the app serves, built before the server opens and again on each refresh, one build at a time.
 *
 * <p>A start loads the files the lock names, each from the cache, else downloaded. A refresh, and a source the lock
 * does not have yet, takes the newest period instead and writes it to the lock. A source that cannot be downloaded or
 * loaded keeps its locked period, or is left out; no single source stops the app. Each build is a new snapshot of the
 * tables, published only when complete ({@link Tables}), then announced with {@link SnapshotPublished}.
 */
@Component
class Snapshot implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);

    private final DataProperties properties;
    private final Downloaders downloaders;
    private final Formats formats;
    private final DSLContext db;
    private final ApplicationEventPublisher events;
    private final Web web;

    /** Checks every source's files and downloader first, so a mistake fails the start, not a refresh. */
    Snapshot(
            DataProperties properties,
            Downloaders downloaders,
            Formats formats,
            Web web,
            DSLContext db,
            ApplicationEventPublisher events) {
        downloaders.validate(properties.sources());
        this.properties = properties;
        this.downloaders = downloaders;
        this.formats = formats;
        this.db = db;
        this.web = web;
        this.events = events;
    }

    /**
     * The first build: once every bean exists, so each {@code @EventListener} hears it, and before the server opens.
     */
    @Override
    public void afterSingletonsInstantiated() {
        build(properties.refresh());
    }

    /** Moves every source to its newest period; the current data serves until the new snapshot is complete. */
    void refresh() {
        build(true);
    }

    /** One build at a time: a second refresh waits for the first. */
    private synchronized void build(boolean refresh) {
        long started = System.nanoTime();
        log.info("Building the data from {} sources", properties.sources().size());
        Map<String, LockedSource> loaded = publish(refresh);
        events.publishEvent(new SnapshotPublished(loaded.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, source -> source.getValue().period()))));
        List<String> leftOut = properties.sources().keySet().stream()
                .filter(not(loaded::containsKey))
                .toList();
        log.info(
                "Data ready in {} s{}",
                Duration.ofNanos(System.nanoTime() - started).toSeconds(),
                leftOut.isEmpty() ? "" : "; left out: " + leftOut);
    }

    /**
     * A new snapshot of every source, published once complete and after the lock is rewritten for the entries that
     * changed, so a lock that cannot be written publishes nothing.
     */
    private Map<String, LockedSource> publish(boolean refresh) {
        Lock lock = Lock.read(properties.lock());
        validate(lock);
        try (Tables tables = Tables.create(
                        db, formats, properties.sql(), properties.sources().keySet());
                Cache cache = new Cache(properties.cacheDir(), downloaders, formats, properties.frozen())) {
            Map<String, LockedSource> loaded = properties.sources().entrySet().stream()
                    .flatMap(source ->
                            load(cache, tables, source.getKey(), source.getValue(), lock.find(source.getKey()), refresh)
                                    .map(entry -> Map.entry(source.getKey(), entry))
                                    .stream())
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            tables.finish();
            Lock next = lock.with(loaded);
            if (!next.equals(lock)) {
                next.write(properties.lock());
                log.info("Wrote {}: review its diff, then commit it", properties.lock());
            }
            tables.publish();
            return loaded;
        }
    }

    /**
     * Loads the first version of {@code source} that loads and returns the lock entry it leaves; empty when none does,
     * and the source is left out. A start loads the locked version. A refresh, and a source the lock does not have yet,
     * tries the newest first, then the locked one, and locks whichever loads. A failed attempt's problem stays on the
     * source's row.
     */
    private Optional<LockedSource> load(
            Cache cache, Tables tables, String name, Source source, Optional<LockedSource> locked, boolean refresh) {
        boolean relocks = !properties.frozen() && (refresh || locked.isEmpty());
        Stream<Supplier<Version>> newest =
                relocks ? Stream.of(() -> cache.download(source, source.periods(web))) : Stream.empty();
        List<Supplier<Version>> attempts = Stream.concat(newest, locked.stream().map(entry -> () -> cache.fetch(entry)))
                .toList();
        List<String> problems = new ArrayList<>();
        for (Supplier<Version> attempt : attempts) {
            try {
                Version version = attempt.get();
                LockedSource entry = relocks ? version.locked() : locked.orElseThrow();
                boolean matchesLock = entry.equals(version.locked());
                long rows = tables.load(name, version, matchesLock, problems);
                cache.keep(version);
                log.info("{}: loaded {} ({} rows)", name, version.period(), rows);
                if (!matchesLock) {
                    log.warn("{}: a file changed upstream since the lock was written; a refresh locks it", name);
                }
                return Optional.of(entry);
            } catch (RuntimeException e) {
                String problem = NestedExceptionUtils.getMostSpecificCause(e).toString();
                log.warn("{}: {}", name, problem);
                log.debug("{} failed", name, e);
                problems.add(problem);
            }
        }
        tables.leaveOut(name, problems);
        return Optional.empty();
    }

    /** @throws IllegalStateException when the start is frozen and the lock lacks a source, before anything downloads */
    private void validate(Lock lock) {
        List<String> unlocked = properties.sources().keySet().stream()
                .filter(name -> lock.find(name).isEmpty())
                .toList();
        Assert.state(
                !properties.frozen() || unlocked.isEmpty(),
                () ->
                        "%s has no entry for %s, and vector.data.frozen never downloads: lock them with a start that is not"
                                .formatted(properties.lock(), unlocked));
    }
}
