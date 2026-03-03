package com.sitionix.forge.outbox.core.model;

import java.util.Arrays;

public enum OutboxStatus {
    PENDING(1L),
    IN_PROGRESS(2L),
    SENT(3L),
    FAILED(4L),
    DEAD(5L);

    private final Long id;

    OutboxStatus(final Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public static OutboxStatus fromId(final Long id) {
        return Arrays.stream(OutboxStatus.values())
                .filter(value -> value.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown OutboxStatus id=" + id));
    }
}
