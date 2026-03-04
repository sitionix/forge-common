package com.sitionix.forge.outbox.core.port;

/**
 * Entry point for writing domain payloads to outbox storage.
 *
 * @param <P> payload type
 */
public interface ForgeOutbox<P extends ForgeOutboxPayload> {

    /**
     * Stores payload as an outbox record.
     *
     * @param payload payload to persist
     */
    void send(P payload);
}
