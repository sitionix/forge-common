package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxRecord;

import java.util.Set;

/**
 * Dispatches outbox records to external transports.
 */
public interface OutboxPublisher {

    /**
     * @return event types that can be published by this instance
     */
    Set<String> supportedEventTypes();

    /**
     * Publishes one claimed outbox record.
     *
     * @param record claimed outbox record
     * @throws Exception publishing failure
     */
    void publish(OutboxRecord record) throws Exception;
}
