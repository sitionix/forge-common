package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.OutboxRecord;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed adapter that decodes payload and delegates to transport-specific publish logic.
 *
 * @param <P> payload type
 */
public abstract class ForgeTypedOutboxEventPublisher<P extends ForgeOutboxPayload> implements ForgeOutboxEventPublisher {

    protected abstract Class<P> payloadClass();

    @Override
    public final boolean tryPublish(final OutboxRecord record,
                                    final OutboxPayloadCodec outboxPayloadCodec) throws Exception {
        Objects.requireNonNull(record, "record is required");
        Objects.requireNonNull(outboxPayloadCodec, "outboxPayloadCodec is required");
        final Class<P> payloadClass = Objects.requireNonNull(this.payloadClass(), "payloadClass is required");

        final P payload;
        try {
            payload = outboxPayloadCodec.deserialize(record.getPayload(), payloadClass);
        } catch (final RuntimeException exception) {
            return false;
        }
        if (payload == null) {
            return false;
        }

        final String payloadEventType = payload.eventType();
        if (payloadEventType == null || payloadEventType.isBlank()) {
            throw new IllegalStateException("eventType is required");
        }
        if (!Objects.equals(payloadEventType, record.getEventType())) {
            return false;
        }

        this.publish(this.buildEvent(record, payload, payloadEventType));
        return true;
    }

    protected abstract void publish(Event<P> event) throws Exception;

    private Event<P> buildEvent(final OutboxRecord record,
                                final P payload,
                                final String payloadEventType) {
        Objects.requireNonNull(record.getCreatedAt(), "createdAt is required");

        return Event.<P>builder()
                .id(record.getId())
                .payload(payload)
                .idempotencyId(this.resolveIdempotencyId(record, payloadEventType))
                .createdAt(record.getCreatedAt())
                .eventType(payloadEventType)
                .build();
    }

    private UUID resolveIdempotencyId(final OutboxRecord record,
                                      final String eventType) {
        final String value = String.join("|",
                eventType == null ? "" : eventType,
                record.getId() == null ? "" : record.getId(),
                record.getCreatedAt() == null ? "" : record.getCreatedAt().toString());
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
