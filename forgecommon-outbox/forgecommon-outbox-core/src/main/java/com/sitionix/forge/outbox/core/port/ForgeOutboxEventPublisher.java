package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.OutboxRecord;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Adapter contract responsible for publishing claimed outbox records.
 */
public interface ForgeOutboxEventPublisher<P extends ForgeOutboxPayload> {

    /**
     * Supported payload class used for decoding outbox payload.
     *
     * @return payload class
     */
    Class<P> payloadClass();

    /**
     * Transport-specific publish operation.
     *
     * @param event decoded and normalized event model
     * @throws Exception publishing failure
     */
    void publish(Event<P> event) throws Exception;

    /**
     * Attempts to publish one claimed outbox record.
     *
     * @param record claimed outbox record
     * @param outboxPayloadCodec payload codec to decode payload
     * @return true when this publisher handled the record; false when unsupported
     * @throws Exception transport-specific publishing failure when the record is supported
     */
    default boolean tryPublish(final OutboxRecord record,
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
