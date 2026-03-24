package com.sitionix.forge.outbox.core.port;

/**
 * Entry point for writing domain payloads to outbox storage.
 *
 * @param <P> payload type
 */
public interface ForgeOutbox<P extends ForgeOutboxPayload> {

    /**
     * Backward-compatible entry point that derives outbox metadata from payload.
     * New code should prefer {@link #send(ForgeOutboxPayload, OutboxSendMetadata)}.
     *
     * @param payload payload to persist
     */
    default void send(final P payload) {
        if (payload == null) {
            send(null, null);
            return;
        }
        send(payload, new OutboxSendMetadata(
                payload.eventType(),
                payload.traceId(),
                payload.headers(),
                payload.metadata(),
                payload.aggregateTypeValue(),
                payload.aggregateId(),
                payload.initiatorType(),
                payload.initiatorId(),
                payload.nextAttemptAt()));
    }

    /**
     * Stores payload as an outbox record.
     *
     * @param payload payload to persist
     * @param metadata outbound metadata used for outbox envelope fields
     */
    void send(P payload, OutboxSendMetadata metadata);
}
