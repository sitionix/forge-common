package com.sitionix.forge.outbox.core.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Metadata supplied by the service when enqueueing an outbox payload.
 *
 * @param eventType logical event type used for routing and storage
 * @param idempotencyId stable idempotency key persisted with the outbox record
 * @param traceId trace id associated with the outbox record
 * @param headers outbound transport headers
 * @param metadata additional envelope metadata
 * @param aggregateType aggregate type value
 * @param aggregateId aggregate id value
 * @param initiatorType initiator type value
 * @param initiatorId initiator id value
 * @param nextAttemptAt first outbox worker attempt timestamp
 */
public record OutboxSendMetadata(
        String eventType,
        UUID idempotencyId,
        String traceId,
        Map<String, String> headers,
        Map<String, String> metadata,
        String aggregateType,
        Long aggregateId,
        String initiatorType,
        String initiatorId,
        Instant nextAttemptAt
) {

    public OutboxSendMetadata {
        headers = toImmutableMap(headers);
        metadata = toImmutableMap(metadata);
    }

    public OutboxSendMetadata(final String eventType,
                              final String traceId,
                              final Map<String, String> headers,
                              final Map<String, String> metadata,
                              final String aggregateType,
                              final Long aggregateId,
                              final String initiatorType,
                              final String initiatorId,
                              final Instant nextAttemptAt) {
        this(eventType, null, traceId, headers, metadata, aggregateType, aggregateId, initiatorType, initiatorId, nextAttemptAt);
    }

    public OutboxSendMetadata(final String eventType) {
        this(eventType, null, null, Map.of(), Map.of(), null, null, null, null, null);
    }

    private static Map<String, String> toImmutableMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
