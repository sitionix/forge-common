package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.ForgeOutbox;
import com.sitionix.forge.outbox.core.port.OutboxSendMetadata;
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
    public void send(final P payload,
                     final OutboxSendMetadata metadata) {
        this.validatePayload(payload);
        final OutboxSendMetadata validatedMetadata = this.validateMetadata(metadata);
        final String encodedPayload = this.resolvePayload(payload);
        final OutboxRecord outboxRecord = this.outboxRecordFactory.create(validatedMetadata, encodedPayload);
        this.storage.enqueue(outboxRecord);
    }

    private void validatePayload(final P payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Outbox payload is required");
        }
    }

    private OutboxSendMetadata validateMetadata(final OutboxSendMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Outbox metadata is required");
        }
        final String eventType = metadata.eventType();
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Outbox eventType is required");
        }
        return metadata;
    }

    private String resolvePayload(final P payload) {
        return this.outboxPayloadCodec.serialize(payload);
    }
}
