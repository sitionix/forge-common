package com.sitionix.forge.inbox.core.model;

/**
 * Service-level inbox event type contract used for eventType-to-payload mapping.
 */
public interface ForgeInboxEventType extends ForgeInboxTypedEnum {

    Class<?> payloadClass();
}
