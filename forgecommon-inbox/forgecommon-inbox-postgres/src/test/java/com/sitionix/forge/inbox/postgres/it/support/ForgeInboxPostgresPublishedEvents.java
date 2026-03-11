package com.sitionix.forge.inbox.postgres.it.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ForgeInboxPostgresPublishedEvents {

    private final CopyOnWriteArrayList<String> eventTypes = new CopyOnWriteArrayList<>();

    public void add(final String eventType) {
        this.eventTypes.add(eventType);
    }

    public List<String> values() {
        return List.copyOf(this.eventTypes);
    }

    public void clear() {
        this.eventTypes.clear();
    }
}
