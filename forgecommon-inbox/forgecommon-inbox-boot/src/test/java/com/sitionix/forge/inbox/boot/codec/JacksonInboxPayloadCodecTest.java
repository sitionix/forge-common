package com.sitionix.forge.inbox.boot.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonInboxPayloadCodecTest {

    private final JacksonInboxPayloadCodec jacksonInboxPayloadCodec = new JacksonInboxPayloadCodec(new ObjectMapper());

    @Test
    void givenPayloadObject_whenSerializeAndDeserialize_thenReturnOriginalPayload() {
        //given
        final Payload given = new Payload("value-1");

        //when
        final String serialized = this.jacksonInboxPayloadCodec.serialize(given);
        final Payload actual = this.jacksonInboxPayloadCodec.deserialize(serialized, Payload.class);

        //then
        assertThat(actual).isEqualTo(given);
    }

    @Test
    void givenRawPayloadString_whenSerialize_thenReturnRawString() {
        //given
        final String given = "{\"event\":\"EMAIL_VERIFY\"}";

        //when
        final String actual = this.jacksonInboxPayloadCodec.serialize(given);

        //then
        assertThat(actual).isEqualTo(given);
    }

    @Test
    void givenRawPayloadString_whenDeserializeToString_thenReturnRawString() {
        //given
        final String given = "{\"event\":\"EMAIL_VERIFY\"}";

        //when
        final String actual = this.jacksonInboxPayloadCodec.deserialize(given, String.class);

        //then
        assertThat(actual).isEqualTo(given);
    }

    @Test
    void givenPayloadWithUnknownField_whenDeserialize_thenThrowException() {
        //given
        final String payload = "{\"value\":\"value-1\",\"unknown\":\"unexpected\"}";

        //when
        //then
        assertThatThrownBy(() -> this.jacksonInboxPayloadCodec.deserialize(payload, Payload.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to deserialize inbox payload");
    }

    private record Payload(String value) {
    }
}
