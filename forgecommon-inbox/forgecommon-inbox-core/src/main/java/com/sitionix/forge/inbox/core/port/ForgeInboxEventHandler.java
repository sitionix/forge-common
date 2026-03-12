package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxEvent;

/**
 * Adapter contract responsible for handling claimed inbox records.
 */
@FunctionalInterface
public interface ForgeInboxEventHandler<P extends ForgeInboxPayload> {

    /**
     * Application-specific handling operation.
     *
     * @param event decoded and normalized event model
     * @throws Exception handling failure
     */
    void handle(InboxEvent<P> event) throws Exception;
}
