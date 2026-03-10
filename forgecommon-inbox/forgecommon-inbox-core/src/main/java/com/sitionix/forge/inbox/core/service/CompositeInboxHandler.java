package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import com.sitionix.forge.inbox.core.port.InboxHandler;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CompositeInboxHandler implements InboxHandler {

    private static final Set<String> ALL_EVENT_TYPES = Set.of("*");

    private final List<ForgeInboxEventHandler<?>> handlers;
    private final InboxPayloadCodec inboxPayloadCodec;

    public CompositeInboxHandler(final List<? extends ForgeInboxEventHandler<?>> handlers,
                                 final InboxPayloadCodec inboxPayloadCodec) {
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers are required"));
        this.inboxPayloadCodec = Objects.requireNonNull(inboxPayloadCodec, "inboxPayloadCodec is required");
    }

    @Override
    public Set<String> supportedEventTypes() {
        return ALL_EVENT_TYPES;
    }

    @Override
    public void handle(final InboxRecord record) throws Exception {
        Objects.requireNonNull(record, "record is required");
        for (final ForgeInboxEventHandler<?> handler : this.handlers) {
            Objects.requireNonNull(handler, "handler is required");
            final boolean handled = handler.tryHandle(record, this.inboxPayloadCodec);
            if (handled) {
                return;
            }
        }
        throw new IllegalStateException("No ForgeInboxEventHandler handled record eventType: " + record.getEventType());
    }
}
