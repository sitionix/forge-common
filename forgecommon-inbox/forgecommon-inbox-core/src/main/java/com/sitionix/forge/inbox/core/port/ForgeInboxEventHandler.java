package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxEvent;

/**
 * Adapter contract responsible for handling claimed inbox records.
 */
public interface ForgeInboxEventHandler<P> {

    /**
     * Application-specific handling operation.
     *
     * @param event decoded and normalized event model
     * @throws Exception handling failure
     */
    void handle(InboxEvent<P> event) throws Exception;
}
