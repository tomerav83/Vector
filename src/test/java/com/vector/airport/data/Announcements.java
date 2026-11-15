package com.vector.airport.data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

/** Each {@link SnapshotPublished} the app announced, as an {@code @EventListener} hears it, e.g. a cache of codes. */
@TestConfiguration(proxyBeanMethods = false)
public class Announcements {

    final List<SnapshotPublished> heard = new CopyOnWriteArrayList<>();

    @EventListener
    void hear(SnapshotPublished snapshot) {
        heard.add(snapshot);
    }
}
