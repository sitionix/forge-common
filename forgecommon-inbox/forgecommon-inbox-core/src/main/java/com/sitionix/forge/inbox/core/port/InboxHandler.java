package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxRecord;

import java.util.Set;

/**
 * Dispatches inbox records to application handlers.
 */
public interface InboxHandler {

    /**
     * @return event types that can be handled by this instance
     */
    Set<String> supportedEventTypes();

    /**
     * Handles one claimed inbox record.
     *
     * @param record claimed inbox record
     * @throws Exception handling failure
     */
    void handle(InboxRecord record) throws Exception;
}
