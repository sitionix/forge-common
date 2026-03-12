package com.sitionix.forge.inbox.postgres.it.support;

import com.sitionix.forge.inbox.core.model.ForgeInboxEventType;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

public enum TestInboxEventType implements ForgeInboxEventType {
    EMAIL_VERIFY(1L, "EMAIL_VERIFY", SuccessInboxPayload.class),
    EMAIL_FAIL(2L, "EMAIL_FAIL", FailingInboxPayload.class);

    private final Long id;
    private final String description;
    private final Class<? extends ForgeInboxPayload> payloadClass;

    TestInboxEventType(final Long id,
                       final String description,
                       final Class<? extends ForgeInboxPayload> payloadClass) {
        this.id = id;
        this.description = description;
        this.payloadClass = payloadClass;
    }

    @Override
    public Long getId() {
        return this.id;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public Class<? extends ForgeInboxPayload> payloadClass() {
        return this.payloadClass;
    }
}
