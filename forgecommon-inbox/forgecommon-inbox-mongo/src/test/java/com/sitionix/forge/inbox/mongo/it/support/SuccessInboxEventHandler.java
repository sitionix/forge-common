package com.sitionix.forge.inbox.mongo.it.support;

import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;

public class SuccessInboxEventHandler implements ForgeInboxEventHandler<SuccessInboxPayload> {

    private final ForgeInboxMongoPublishedEvents publishedEvents;

    public SuccessInboxEventHandler(final ForgeInboxMongoPublishedEvents publishedEvents) {
        this.publishedEvents = publishedEvents;
    }

    @Override
    public void handle(final InboxEvent<SuccessInboxPayload> event) {
        this.publishedEvents.add(event.getEventType());
    }
}
