package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class OutboxRecordFactory {

    private final Clock clock;

    public OutboxRecordFactory(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public OutboxRecord create(final ForgeOutboxPayload payload,
                               final String eventType) {
        final Instant now = Instant.now(this.clock);
        final String aggregateType = this.normalize(payload.aggregateTypeValue());
        final Long aggregateId = payload.aggregateId();

        return OutboxRecord.builder()
                .eventType(eventType)
                .payload(null)
                .headers(defaultMap(payload.headers()))
                .metadata(defaultMap(payload.metadata()))
                .traceId(payload.traceId())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .initiatorType(payload.initiatorType())
                .initiatorId(payload.initiatorId())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(payload.nextAttemptAt() == null ? now : payload.nextAttemptAt())
                .lastError(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public OutboxRecord withPayload(final OutboxRecord outboxRecord,
                                    final String encodedPayload) {
        return outboxRecord.toBuilder()
                .payload(encodedPayload)
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
