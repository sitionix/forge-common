package com.sitionix.forge.outbox.core.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract that defines metadata required to build outbound events.
 */
public interface EventMetadataContract extends ForgeOutboxPayload {

    /**
     * @return idempotency identifier for the event.
     */
    UUID getIdempotencyId();

    /**
     * @return event creation timestamp.
     */
    Instant getCreatedAt();

    /**
     * @return event type.
     */
    String getEventType();
}
