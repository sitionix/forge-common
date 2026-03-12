package com.sitionix.forge.inbox.core.model;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

/**
 * Service-level inbox event type contract used for eventType-to-payload mapping.
 */
public interface ForgeInboxEventType extends ForgeInboxTypedEnum {

    /**
     * @return payload class that should be used for deserialization and handler resolution
     */
    Class<? extends ForgeInboxPayload> payloadClass();
}
