package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.InboxReceiveMetadata;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class InboxRecordFactory {

    private final Clock clock;

    public InboxRecordFactory(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public InboxRecord create(final InboxReceiveMetadata receiveMetadata,
                              final String encodedPayload) {
        Objects.requireNonNull(receiveMetadata, "receiveMetadata is required");
        Objects.requireNonNull(encodedPayload, "encodedPayload is required");
        final Instant now = Instant.now(this.clock);
        final String aggregateType = this.normalize(receiveMetadata.aggregateType());
        final Long aggregateId = receiveMetadata.aggregateId();
        final String traceId = this.normalize(receiveMetadata.traceId());

        return InboxRecord.builder()
                .eventType(this.normalize(receiveMetadata.eventType()))
                .payload(encodedPayload)
                .headers(defaultMap(receiveMetadata.headers()))
                .metadata(defaultMap(receiveMetadata.metadata()))
                .traceId(traceId)
                .idempotencyKey(this.normalize(receiveMetadata.idempotencyKey()))
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .initiatorType(this.normalize(receiveMetadata.initiatorType()))
                .initiatorId(this.normalize(receiveMetadata.initiatorId()))
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(receiveMetadata.nextAttemptAt() == null ? now : receiveMetadata.nextAttemptAt())
                .lastError(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, String> defaultMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
