package com.sitionix.forge.inbox.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
public class InboxRecord {

    private final String id;
    private final String eventType;
    private final String payload;
    private final Map<String, String> headers;
    private final Map<String, String> metadata;
    private final String traceId;
    private final String idempotencyKey;
    private final String aggregateType;
    private final Long aggregateId;
    private final String initiatorType;
    private final String initiatorId;
    private final InboxStatus status;
    private final int attempts;
    private final Instant nextAttemptAt;
    private final String lastError;
    private final Instant createdAt;
    private final Instant updatedAt;
}
