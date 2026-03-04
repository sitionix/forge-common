package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.ForgeOutbox;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxStorage;

import java.time.Clock;
import java.util.Objects;

public class DefaultForgeOutbox<P extends ForgeOutboxPayload> implements ForgeOutbox<P> {

    private final OutboxStorage storage;
    private final OutboxPayloadCodec outboxPayloadCodec;
    private final OutboxRecordFactory outboxRecordFactory;

    public DefaultForgeOutbox(final OutboxStorage storage,
                              final Clock clock,
                              final OutboxPayloadCodec outboxPayloadCodec) {
        this.storage = Objects.requireNonNull(storage, "storage is required");
        this.outboxPayloadCodec = Objects.requireNonNull(outboxPayloadCodec, "outboxPayloadCodec is required");
        this.outboxRecordFactory = new OutboxRecordFactory(Objects.requireNonNull(clock, "clock is required"));
    }

    @Override
    public void send(final P payload) {
        final String eventType = this.validateAndResolveEventType(payload);
        final OutboxRecord outboxRecord = this.outboxRecordFactory.create(payload, eventType);
        final String encodedPayload = this.resolvePayload(payload);
        this.storage.enqueue(this.outboxRecordFactory.withPayload(outboxRecord, encodedPayload));
    }

    private String validateAndResolveEventType(final P payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Outbox payload is required");
        }
        final String eventType = this.resolveEventType(payload);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Outbox eventType is required");
        }
        return eventType;
    }

    private String resolveEventType(final P payload) {
        return payload.eventType();
    }

    private String resolvePayload(final P payload) {
        return this.outboxPayloadCodec.serialize(payload);
    }
}
