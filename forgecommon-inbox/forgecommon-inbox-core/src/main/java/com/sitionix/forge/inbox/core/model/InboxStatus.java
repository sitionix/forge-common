package com.sitionix.forge.inbox.core.model;

import java.util.Arrays;

public enum InboxStatus {
    PENDING(1L),
    IN_PROGRESS(2L),
    PROCESSED(3L),
    FAILED(4L),
    DEAD(5L);

    private final Long id;

    InboxStatus(final Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public static InboxStatus fromId(final Long id) {
        return Arrays.stream(InboxStatus.values())
                .filter(value -> value.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown InboxStatus id=" + id));
    }
}
