package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.Event;

/**
 * Adapter contract responsible for publishing a concrete outbox payload type.
 *
 * @param <P> payload type
 */
public interface ForgeOutboxEventPublisher<P extends ForgeOutboxPayload> {

    /**
     * @return outbox event type supported by this publisher
     */
    String eventType();

    /**
     * @return payload class expected by this publisher
     */
    Class<P> payloadType();

    /**
     * Publishes the decoded outbox event.
     *
     * @param event decoded event envelope
     * @throws Exception transport-specific publishing failure
     */
    void publish(Event<P> event) throws Exception;
}
