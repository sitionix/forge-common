package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
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

    public InboxRecord create(final ForgeInboxPayload payload,
                              final InboxReceiveMetadata receiveMetadata) {
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(receiveMetadata, "receiveMetadata is required");
        final Instant now = Instant.now(this.clock);
        final String aggregateType = this.normalize(payload.aggregateTypeValue());
        final Long aggregateId = payload.aggregateId();
        final String traceId = this.resolveTraceId(payload, receiveMetadata);

        return InboxRecord.builder()
                .eventType(this.normalize(receiveMetadata.eventType()))
                .payload(null)
                .headers(defaultMap(payload.headers()))
                .metadata(defaultMap(payload.metadata()))
                .traceId(traceId)
                .idempotencyKey(this.normalize(receiveMetadata.idempotencyKey()))
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

    private String resolveTraceId(final ForgeInboxPayload payload,
                                  final InboxReceiveMetadata receiveMetadata) {
        final String metadataTraceId = this.normalize(receiveMetadata.traceId());
        if (metadataTraceId != null) {
            return metadataTraceId;
        }
        return this.normalize(payload.traceId());
    }

    private static Map<String, String> defaultMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
