package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class OutboxRecordFactory {

    private final Clock clock;

    public OutboxRecordFactory(final Clock clock) {
        this.clock = clock;
    }

    public OutboxRecord create(final ForgeOutboxPayload payload,
                               final String eventType) {
        final Instant now = Instant.now(this.clock);
        final Long aggregateId = payload.getAggregateId();

        return OutboxRecord.builder()
                .eventType(eventType)
                .payload(null)
                .headers(defaultMap(payload.getOutboxHeaders()))
                .metadata(defaultMap(payload.getOutboxMetadata()))
                .traceId(payload.getOutboxTraceId())
                .aggregateType(aggregateId == null || payload.getAggregateType() == null
                        ? null
                        : payload.getAggregateType().getDescription())
                .aggregateId(aggregateId)
                .initiatorType(aggregateId == null ? null : payload.getOutboxInitiatorType())
                .initiatorId(aggregateId == null ? null : payload.getOutboxInitiatorId())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(payload.getOutboxNextAttemptAt() == null ? now : payload.getOutboxNextAttemptAt())
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

    private static Map<String, String> defaultMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
