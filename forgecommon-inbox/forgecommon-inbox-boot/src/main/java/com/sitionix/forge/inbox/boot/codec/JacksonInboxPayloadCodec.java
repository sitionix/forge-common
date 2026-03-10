package com.sitionix.forge.inbox.boot.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JacksonInboxPayloadCodec implements InboxPayloadCodec {

    private final ObjectMapper objectMapper;

    @Override
    public String serialize(final Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String rawPayload) {
            return rawPayload;
        }
        try {
            return this.objectMapper.writeValueAsString(payload);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize inbox payload", exception);
        }
    }

    @Override
    public <T> T deserialize(final String payload, final Class<T> payloadType) {
        if (payload == null) {
            return null;
        }
        if (String.class.equals(payloadType)) {
            return payloadType.cast(payload);
        }
        try {
            return this.objectMapper.readValue(payload, payloadType);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize inbox payload", exception);
        }
    }
}
