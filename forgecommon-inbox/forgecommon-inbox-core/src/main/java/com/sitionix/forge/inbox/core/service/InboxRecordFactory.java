package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class InboxRecordFactory {

    private final Clock clock;

    public InboxRecordFactory(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public InboxRecord create(final ForgeInboxPayload payload,
                               final String eventType) {
        final Instant now = Instant.now(this.clock);
        final String aggregateType = this.normalize(payload.aggregateTypeValue());
        final Long aggregateId = payload.aggregateId();

        return InboxRecord.builder()
                .eventType(eventType)
                .payload(null)
                .headers(defaultMap(payload.headers()))
                .metadata(defaultMap(payload.metadata()))
                .traceId(payload.traceId())
                .idempotencyKey(this.normalize(payload.idempotencyKey()))
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .initiatorType(payload.initiatorType())
                .initiatorId(payload.initiatorId())
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(payload.nextAttemptAt() == null ? now : payload.nextAttemptAt())
                .lastError(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public InboxRecord withPayload(final InboxRecord inboxRecord,
                                    final String encodedPayload) {
        return inboxRecord.toBuilder()
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
