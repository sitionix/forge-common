package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.InboxReceiveMetadata;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import com.sitionix.forge.inbox.core.port.InboxStorage;

import java.time.Clock;
import java.util.Objects;

public class DefaultForgeInbox<P extends ForgeInboxPayload> implements ForgeInbox<P> {

    private final InboxStorage storage;
    private final InboxPayloadCodec inboxPayloadCodec;
    private final InboxRecordFactory inboxRecordFactory;

    public DefaultForgeInbox(final InboxStorage storage,
                              final Clock clock,
                              final InboxPayloadCodec inboxPayloadCodec) {
        this.storage = Objects.requireNonNull(storage, "storage is required");
        this.inboxPayloadCodec = Objects.requireNonNull(inboxPayloadCodec, "inboxPayloadCodec is required");
        this.inboxRecordFactory = new InboxRecordFactory(Objects.requireNonNull(clock, "clock is required"));
    }

    @Override
    public void receive(final P payload,
                        final InboxReceiveMetadata metadata) {
        this.validatePayload(payload);
        final InboxReceiveMetadata validatedMetadata = this.validateMetadata(metadata);
        final String encodedPayload = this.resolvePayload(payload);
        this.storage.enqueue(this.inboxRecordFactory.create(validatedMetadata, encodedPayload));
    }

    private void validatePayload(final P payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Inbox payload is required");
        }
    }

    private InboxReceiveMetadata validateMetadata(final InboxReceiveMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Inbox metadata is required");
        }
        final String eventType = metadata.eventType();
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Inbox eventType is required");
        }
        final String idempotencyKey = metadata.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Inbox idempotencyKey is required");
        }
        return metadata;
    }

    private String resolvePayload(final P payload) {
        return this.inboxPayloadCodec.serialize(payload);
    }
}
