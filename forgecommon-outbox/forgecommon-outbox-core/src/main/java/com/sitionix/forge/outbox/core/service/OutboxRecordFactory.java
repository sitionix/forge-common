package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.OutboxSendMetadata;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class OutboxRecordFactory {

    private final Clock clock;

    public OutboxRecordFactory(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public OutboxRecord create(final OutboxSendMetadata sendMetadata,
                               final String encodedPayload) {
        Objects.requireNonNull(sendMetadata, "sendMetadata is required");
        Objects.requireNonNull(encodedPayload, "encodedPayload is required");
        final Instant now = Instant.now(this.clock);
        final String aggregateType = this.normalize(sendMetadata.aggregateType());
        final Long aggregateId = sendMetadata.aggregateId();
        final String traceId = this.normalize(sendMetadata.traceId());

        return OutboxRecord.builder()
                .eventType(this.normalize(sendMetadata.eventType()))
                .payload(encodedPayload)
                .idempotencyId(sendMetadata.idempotencyId() == null ? UUID.randomUUID() : sendMetadata.idempotencyId())
                .headers(defaultMap(sendMetadata.headers()))
                .metadata(defaultMap(sendMetadata.metadata()))
                .traceId(traceId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .initiatorType(this.normalize(sendMetadata.initiatorType()))
                .initiatorId(this.normalize(sendMetadata.initiatorId()))
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(sendMetadata.nextAttemptAt() == null ? now : sendMetadata.nextAttemptAt())
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
