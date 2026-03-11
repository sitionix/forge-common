package com.sitionix.forge.inbox.core.port;

/**
 * Metadata extracted from an inbound event envelope and used to persist inbox record metadata.
 *
 * @param eventType logical event type from transport metadata
 * @param idempotencyKey idempotency key from transport metadata
 * @param traceId trace id from transport metadata
 */
public record InboxReceiveMetadata(
        String eventType,
        String idempotencyKey,
        String traceId
) {
}
