package com.sitionix.forge.inbox.core.port;

/**
 * Converts inbox payloads between in-memory objects and persisted string form.
 */
public interface InboxPayloadCodec {

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
