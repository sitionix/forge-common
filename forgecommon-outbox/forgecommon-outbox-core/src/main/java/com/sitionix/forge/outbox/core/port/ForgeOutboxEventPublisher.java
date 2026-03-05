package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxRecord;

/**
 * Adapter contract responsible for publishing claimed outbox records.
 */
public interface ForgeOutboxEventPublisher {

    /**
     * Attempts to publish one claimed outbox record.
     *
     * @param record claimed outbox record
     * @param outboxPayloadCodec payload codec to decode payload
     * @return true when this publisher handled the record; false when unsupported
     * @throws Exception transport-specific publishing failure when the record is supported
     */
    boolean tryPublish(OutboxRecord record,
                       OutboxPayloadCodec outboxPayloadCodec) throws Exception;
}
