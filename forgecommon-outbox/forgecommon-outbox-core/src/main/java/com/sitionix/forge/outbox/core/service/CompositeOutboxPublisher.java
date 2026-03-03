package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CompositeOutboxPublisher implements OutboxPublisher {

    private final Map<String, ForgeOutboxEventPublisher<?>> publishersByEventType;
    private final OutboxPayloadCodec outboxPayloadCodec;

    public CompositeOutboxPublisher(final List<? extends ForgeOutboxEventPublisher<?>> publishers,
                                    final OutboxPayloadCodec outboxPayloadCodec) {
        this.publishersByEventType = this.indexPublishers(publishers);
        this.outboxPayloadCodec = outboxPayloadCodec;
    }

    @Override
    public Set<String> supportedEventTypes() {
        return Set.copyOf(this.publishersByEventType.keySet());
    }

    @Override
    public void publish(final OutboxRecord record) throws Exception {
        final ForgeOutboxEventPublisher<?> publisher = this.publishersByEventType.get(record.getEventType());
        if (publisher == null) {
            throw new IllegalStateException("No ForgeOutboxEventPublisher registered for event type: " + record.getEventType());
        }
        this.publishTyped(record, publisher);
    }

    private <P extends ForgeOutboxPayload> void publishTyped(final OutboxRecord record,
                                                             final ForgeOutboxEventPublisher<P> publisher) throws Exception {
        final P payload = this.outboxPayloadCodec.deserialize(record.getPayload(), publisher.payloadType());
        publisher.publish(this.buildEvent(record, payload));
    }

    private <P extends ForgeOutboxPayload> Event<P> buildEvent(final OutboxRecord record,
                                                               final P payload) {
        this.validateEventFields(record, payload);
        return Event.<P>builder()
                .id(record.getId())
                .payload(payload)
                .idempotencyId(this.resolveIdempotencyId(record))
                .createdAt(record.getCreatedAt())
                .eventType(record.getEventType())
                .build();
    }

    private <P extends ForgeOutboxPayload> void validateEventFields(final OutboxRecord record,
                                                                    final P payload) {
        if (payload == null) {
            throw new IllegalStateException("Outbox payload is required");
        }
        Objects.requireNonNull(record.getCreatedAt(), "createdAt is required");
        final String eventType = Objects.requireNonNull(record.getEventType(), "eventType is required");
        if (eventType.isBlank()) {
            throw new IllegalStateException("eventType is required");
        }
    }

    private UUID resolveIdempotencyId(final OutboxRecord record) {
        final String value = String.join("|",
                record.getEventType() == null ? "" : record.getEventType(),
                record.getId() == null ? "" : record.getId(),
                record.getCreatedAt() == null ? "" : record.getCreatedAt().toString());
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, ForgeOutboxEventPublisher<?>> indexPublishers(final List<? extends ForgeOutboxEventPublisher<?>> publishers) {
        final Map<String, ForgeOutboxEventPublisher<?>> result = new LinkedHashMap<>();
        for (final ForgeOutboxEventPublisher<?> publisher : publishers) {
            final String eventType = publisher.eventType();
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalStateException("ForgeOutboxEventPublisher event type is required");
            }
            final ForgeOutboxEventPublisher<?> existing = result.putIfAbsent(eventType, publisher);
            if (existing != null) {
                throw new IllegalStateException("Duplicate ForgeOutboxEventPublisher registration for event type: " + eventType);
            }
        }
        return Map.copyOf(result);
    }
}
