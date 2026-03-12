package com.sitionix.forge.inbox.core.port;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata extracted from an inbound event envelope and used to persist inbox record metadata.
 *
 * @param eventType logical event type from transport metadata
 * @param idempotencyKey idempotency key from transport metadata
 * @param traceId trace id from transport metadata
 * @param headers inbound transport headers
 * @param metadata additional envelope metadata
 * @param aggregateType aggregate type value
 * @param aggregateId aggregate id value
 * @param initiatorType initiator type value
 * @param initiatorId initiator id value
 * @param nextAttemptAt first inbox worker attempt timestamp
 */
public record InboxReceiveMetadata(
        String eventType,
        String idempotencyKey,
        String traceId,
        Map<String, String> headers,
        Map<String, String> metadata,
        String aggregateType,
        Long aggregateId,
        String initiatorType,
        String initiatorId,
    Instant nextAttemptAt
) {

    public InboxReceiveMetadata {
        headers = toImmutableMap(headers);
        metadata = toImmutableMap(metadata);
    }

    public InboxReceiveMetadata(final String eventType,
                                final String idempotencyKey,
                                final String traceId) {
        this(eventType, idempotencyKey, traceId, Map.of(), Map.of(), null, null, null, null, null);
    }

    private static Map<String, String> toImmutableMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
