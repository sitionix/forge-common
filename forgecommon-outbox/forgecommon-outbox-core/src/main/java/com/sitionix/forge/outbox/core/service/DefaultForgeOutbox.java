package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.EventMetadataContract;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.ForgeOutbox;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxStorage;

import java.time.Clock;

public class DefaultForgeOutbox<P extends ForgeOutboxPayload> implements ForgeOutbox<P> {

    private final OutboxStorage storage;
    private final OutboxPayloadCodec outboxPayloadCodec;
    private final OutboxRecordFactory outboxRecordFactory;

    public DefaultForgeOutbox(final OutboxStorage storage,
                              final Clock clock) {
        this(storage, clock, null);
    }

    public DefaultForgeOutbox(final OutboxStorage storage,
                              final Clock clock,
                              final OutboxPayloadCodec outboxPayloadCodec) {
        this.storage = storage;
        this.outboxPayloadCodec = outboxPayloadCodec;
        this.outboxRecordFactory = new OutboxRecordFactory(clock);
    }

    @Override
    public void send(final P payload) {
        this.validate(payload);
        final String outboxEventType = this.resolveOutboxEventType(payload);
        final OutboxRecord outboxRecord = this.outboxRecordFactory.create(payload, outboxEventType);
        final String encodedPayload = this.resolvePayload(payload);
        this.storage.enqueue(this.outboxRecordFactory.withPayload(outboxRecord, encodedPayload));
    }

    private void validate(final P payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Outbox payload is required");
        }
        final String outboxEventType = this.resolveOutboxEventType(payload);
        if (outboxEventType == null || outboxEventType.isBlank()) {
            throw new IllegalArgumentException("Outbox eventType is required");
        }
    }

    private String resolveOutboxEventType(final P payload) {
        final String outboxEventType = payload.getOutboxEventType();
        if (outboxEventType != null && !outboxEventType.isBlank()) {
            return outboxEventType;
        }
        if (payload instanceof EventMetadataContract metadataContract) {
            return metadataContract.getEventType();
        }
        return outboxEventType;
    }

    private String resolvePayload(final P payload) {
        if (this.outboxPayloadCodec == null) {
            throw new IllegalStateException("Outbox payload codec is required for object payload");
        }
        return this.outboxPayloadCodec.serialize(payload);
    }
}
