package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxAggregateType;

import java.time.Instant;
import java.util.Map;

/**
 * Marker contract for payloads that can be persisted into the outbox.
 *
 * Legacy metadata methods remain for backward compatibility with services that
 * still use the older {@code forgeOutbox.send(payload)} style. New code should
 * prefer {@link ForgeOutbox#send(ForgeOutboxPayload, OutboxSendMetadata)} and
 * keep transport metadata outside payloads.
 */
public interface ForgeOutboxPayload {

    default String eventType() {
        return null;
    }

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
