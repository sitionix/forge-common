package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxAggregateType;

import java.time.Instant;
import java.util.Map;

/**
 * Payload contract that describes how domain data is mapped into an inbox record.
 */
public interface ForgeInboxPayload {

    /**
     * @return logical inbox event type used for routing to publishers
     */
    String eventType();

    default Map<String, String> headers() {
        return Map.of();
    }

    default Map<String, String> metadata() {
        return Map.of();
    }

    default String traceId() {
        return null;
    }

    /**
     * @return idempotency key used for duplicate processing protection.
     */
    default String idempotencyKey() {
        return null;
    }

    default InboxAggregateType aggregateType() {
        return null;
    }

    /**
     * @return aggregate type name for storage/routing
     */
    default String aggregateTypeValue() {
        final InboxAggregateType aggregateType = this.aggregateType();
        return aggregateType == null ? null : aggregateType.getDescription();
    }

    default Long aggregateId() {
        return null;
    }

    default String initiatorType() {
        return null;
    }

    default String initiatorId() {
        return null;
    }

    default Instant nextAttemptAt() {
        return null;
    }
}
