package com.sitionix.forge.inbox.core.model;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class InboxEvent<P extends ForgeInboxPayload> {

    private final String id;
    private final P payload;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final String eventType;
}
