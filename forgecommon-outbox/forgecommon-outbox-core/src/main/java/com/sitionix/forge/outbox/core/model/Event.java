package com.sitionix.forge.outbox.core.model;

import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class Event<P extends ForgeOutboxPayload> {

    private final String id;
    private final P payload;
    private final UUID idempotencyId;
    private final Instant createdAt;
    private final String eventType;
}
