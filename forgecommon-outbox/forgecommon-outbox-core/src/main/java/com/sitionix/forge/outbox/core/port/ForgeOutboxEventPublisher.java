package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.Event;

/**
 * Adapter contract responsible for publishing claimed outbox records.
 */
public interface ForgeOutboxEventPublisher<P extends ForgeOutboxPayload> {

    /**
     * Transport-specific publish operation.
     *
     * @param event decoded and normalized event model
     * @throws Exception publishing failure
     */
    void publish(Event<P> event) throws Exception;
}
