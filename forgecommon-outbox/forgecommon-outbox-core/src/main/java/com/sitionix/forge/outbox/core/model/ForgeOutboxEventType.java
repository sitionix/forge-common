package com.sitionix.forge.outbox.core.model;

import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;

/**
 * Service-level outbox event type contract used for eventType-to-payload mapping.
 */
public interface ForgeOutboxEventType extends ForgeOutboxTypedEnum {

    /**
     * @return payload class that should be used for deserialization and publisher resolution
     */
    Class<? extends ForgeOutboxPayload> payloadClass();
}
