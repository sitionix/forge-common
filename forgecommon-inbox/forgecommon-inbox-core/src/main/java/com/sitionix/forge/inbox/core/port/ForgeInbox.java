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
     */
    void receive(P payload);
}
