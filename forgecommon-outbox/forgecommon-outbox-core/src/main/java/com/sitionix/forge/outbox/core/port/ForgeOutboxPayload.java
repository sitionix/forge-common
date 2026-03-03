package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxAggregateType;

import java.time.Instant;
import java.util.Map;

public interface ForgeOutboxPayload {

    default String eventType() {
        return null;
    }

    default String getOutboxEventType() {
        return this.eventType();
    }

    default Map<String, String> getOutboxHeaders() {
        return Map.of();
    }

    default Map<String, String> getOutboxMetadata() {
        return Map.of();
    }

    default String getOutboxTraceId() {
        return null;
    }

    default OutboxAggregateType getAggregateType() {
        return this.getAgregateType();
    }

    default Long getAggregateId() {
        return this.getAgregateId();
    }

    @Deprecated(forRemoval = false)
    default OutboxAggregateType getAgregateType() {
        return null;
    }

    @Deprecated(forRemoval = false)
    default Long getAgregateId() {
        return null;
    }

    default String getOutboxInitiatorType() {
        return null;
    }

    default String getOutboxInitiatorId() {
        return null;
    }

    default Instant getOutboxNextAttemptAt() {
        return null;
    }
}
