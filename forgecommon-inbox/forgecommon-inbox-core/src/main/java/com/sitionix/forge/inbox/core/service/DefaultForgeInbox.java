package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
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
    public void receive(final P payload) {
        final String eventType = this.validateAndResolveEventType(payload);
        final InboxRecord inboxRecord = this.inboxRecordFactory.create(payload, eventType);
        final String encodedPayload = this.resolvePayload(payload);
        this.storage.enqueue(this.inboxRecordFactory.withPayload(inboxRecord, encodedPayload));
    }

    private String validateAndResolveEventType(final P payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Inbox payload is required");
        }
        final String eventType = this.resolveEventType(payload);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Inbox eventType is required");
        }
        return eventType;
    }

    private String resolveEventType(final P payload) {
        return payload.eventType();
    }

    private String resolvePayload(final P payload) {
        return this.inboxPayloadCodec.serialize(payload);
    }
}
