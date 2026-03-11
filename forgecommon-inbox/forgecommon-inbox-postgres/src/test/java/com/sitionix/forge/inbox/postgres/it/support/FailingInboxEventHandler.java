package com.sitionix.forge.inbox.postgres.it.support;

import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;

public class FailingInboxEventHandler implements ForgeInboxEventHandler<FailingInboxPayload> {

    @Override
    public void handle(final InboxEvent<FailingInboxPayload> event) {
        throw new IllegalStateException("Forced publish failure");
    }
}
