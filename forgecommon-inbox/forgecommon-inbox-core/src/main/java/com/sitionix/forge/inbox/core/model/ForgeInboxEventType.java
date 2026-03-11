package com.sitionix.forge.inbox.core.model;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

/**
 * Service-level inbox event type contract used for eventType-to-payload mapping.
 */
public interface ForgeInboxEventType extends ForgeInboxTypedEnum {

    Class<? extends ForgeInboxPayload> payloadClass();
}
