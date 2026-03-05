package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CompositeOutboxPublisher implements OutboxPublisher {

    private static final Set<String> ALL_EVENT_TYPES = Set.of("*");

    private final List<ForgeOutboxEventPublisher> publishers;
    private final OutboxPayloadCodec outboxPayloadCodec;

    public CompositeOutboxPublisher(final List<? extends ForgeOutboxEventPublisher> publishers,
                                    final OutboxPayloadCodec outboxPayloadCodec) {
        this.publishers = List.copyOf(Objects.requireNonNull(publishers, "publishers are required"));
        this.outboxPayloadCodec = Objects.requireNonNull(outboxPayloadCodec, "outboxPayloadCodec is required");
    }

    @Override
    public Set<String> supportedEventTypes() {
        return ALL_EVENT_TYPES;
    }

    @Override
    public void publish(final OutboxRecord record) throws Exception {
        Objects.requireNonNull(record, "record is required");
        for (final ForgeOutboxEventPublisher publisher : this.publishers) {
            Objects.requireNonNull(publisher, "publisher is required");
            final boolean handled = publisher.tryPublish(record, this.outboxPayloadCodec);
            if (handled) {
                return;
            }
        }
        throw new IllegalStateException("No ForgeOutboxEventPublisher handled record eventType: " + record.getEventType());
    }
}
