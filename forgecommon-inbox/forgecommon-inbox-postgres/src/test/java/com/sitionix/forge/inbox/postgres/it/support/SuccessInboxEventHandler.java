package com.sitionix.forge.inbox.postgres.it.support;

import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;

public class SuccessInboxEventHandler implements ForgeInboxEventHandler<SuccessInboxPayload> {

    private final ForgeInboxPostgresPublishedEvents publishedEvents;

    public SuccessInboxEventHandler(final ForgeInboxPostgresPublishedEvents publishedEvents) {
        this.publishedEvents = publishedEvents;
    }

    @Override
    public void handle(final InboxEvent<SuccessInboxPayload> event) {
        this.publishedEvents.add(event.getEventType());
    }
}
