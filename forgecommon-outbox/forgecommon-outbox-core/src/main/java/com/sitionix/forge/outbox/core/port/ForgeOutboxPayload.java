package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxAggregateType;

import java.time.Instant;
import java.util.Map;

/**
 * Payload contract that describes how domain data is mapped into an outbox record.
 */
public interface ForgeOutboxPayload {

    /**
     * @return logical outbox event type used for routing to publishers
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

    default OutboxAggregateType aggregateType() {
        return null;
    }

    /**
     * @return aggregate type name for storage/routing
     */
    default String aggregateTypeValue() {
        final OutboxAggregateType aggregateType = this.aggregateType();
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
