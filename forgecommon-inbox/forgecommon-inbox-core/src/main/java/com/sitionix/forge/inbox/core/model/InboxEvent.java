package com.sitionix.forge.inbox.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class InboxEvent<P> {

    private final String id;
    private final P payload;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final String eventType;
}
