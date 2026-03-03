package com.sitionix.forge.outbox.core.model;

import java.util.Arrays;

public enum OutboxAggregateType {
    USER(1L, "USER");

    private final Long id;
    private final String description;

    OutboxAggregateType(final Long id,
                        final String description) {
        this.id = id;
        this.description = description;
    }

    public Long getId() {
        return this.id;
    }

    public String getDescription() {
        return this.description;
    }

    public static OutboxAggregateType fromId(final Long id) {
        return Arrays.stream(OutboxAggregateType.values())
                .filter(value -> value.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown OutboxAggregateType id=" + id));
    }

    public static OutboxAggregateType fromDescription(final String description) {
        return Arrays.stream(OutboxAggregateType.values())
                .filter(value -> value.description.equals(description))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown OutboxAggregateType description=" + description));
    }
}
