package com.sitionix.forge.outbox.core.port;

/**
 * Converts outbox payloads between in-memory objects and persisted string form.
 */
public interface OutboxPayloadCodec {

    /**
     * Serializes payload object into storage-ready string representation.
     *
     * @param payload payload object
     * @return serialized payload
     */
    String serialize(Object payload);

    /**
     * Deserializes payload string into a target type.
     *
     * @param payload payload string
     * @param payloadType target payload class
     * @return deserialized payload object
     * @param <T> target type
     */
    <T> T deserialize(String payload, Class<T> payloadType);
}
