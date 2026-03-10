package com.sitionix.forge.inbox.core.model;

import java.util.Arrays;

public enum InboxAggregateType {
    USER(1L, "USER");

    private final Long id;
    private final String description;

    InboxAggregateType(final Long id,
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

    public static InboxAggregateType fromId(final Long id) {
        return Arrays.stream(InboxAggregateType.values())
                .filter(value -> value.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown InboxAggregateType id=" + id));
    }

    public static InboxAggregateType fromDescription(final String description) {
        return Arrays.stream(InboxAggregateType.values())
                .filter(value -> value.description.equals(description))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown InboxAggregateType description=" + description));
    }
}
