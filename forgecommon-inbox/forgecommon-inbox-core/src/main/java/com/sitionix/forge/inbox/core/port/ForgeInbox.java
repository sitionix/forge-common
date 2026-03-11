package com.sitionix.forge.inbox.core.port;

/**
 * Entry point for writing domain payloads to inbox storage.
 *
 * @param <P> payload type
 */
public interface ForgeInbox<P extends ForgeInboxPayload> {

    /**
     * Stores payload as an inbox record.
     *
     * @param payload payload to persist
     * @param metadata inbound metadata used for inbox envelope fields
     */
    void receive(P payload, InboxReceiveMetadata metadata);
}
