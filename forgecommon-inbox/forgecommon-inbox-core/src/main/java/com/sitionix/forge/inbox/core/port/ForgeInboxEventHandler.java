package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.model.InboxRecord;

import java.util.Objects;

/**
 * Adapter contract responsible for handling claimed inbox records.
 */
public interface ForgeInboxEventHandler<P extends ForgeInboxPayload> {

    /**
     * Supported payload class used for decoding inbox payload.
     *
     * @return payload class
     */
    Class<P> payloadClass();

    /**
     * Application-specific handling operation.
     *
     * @param event decoded and normalized event model
     * @throws Exception handling failure
     */
    void handle(InboxEvent<P> event) throws Exception;

    /**
     * Attempts to handle one claimed inbox record.
     *
     * @param record claimed inbox record
     * @param inboxPayloadCodec payload codec to decode payload
     * @return true when this handler processed the record; false when unsupported
     * @throws Exception handling failure when the record is supported
     */
    default boolean tryHandle(final InboxRecord record,
                               final InboxPayloadCodec inboxPayloadCodec) throws Exception {
        Objects.requireNonNull(record, "record is required");
        Objects.requireNonNull(inboxPayloadCodec, "inboxPayloadCodec is required");

        final Class<P> payloadClass = Objects.requireNonNull(this.payloadClass(), "payloadClass is required");
        final P payload;
        try {
            payload = inboxPayloadCodec.deserialize(record.getPayload(), payloadClass);
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

        this.handle(this.buildEvent(record, payload, payloadEventType));
        return true;
    }

    private InboxEvent<P> buildEvent(final InboxRecord record,
                                final P payload,
                                final String payloadEventType) {
        Objects.requireNonNull(record.getCreatedAt(), "createdAt is required");

        return InboxEvent.<P>builder()
                .id(record.getId())
                .payload(payload)
                .idempotencyKey(record.getIdempotencyKey())
                .createdAt(record.getCreatedAt())
                .eventType(payloadEventType)
                .build();
    }
}
