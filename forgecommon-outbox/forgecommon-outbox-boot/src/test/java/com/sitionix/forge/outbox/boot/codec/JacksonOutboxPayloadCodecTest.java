package com.sitionix.forge.outbox.boot.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonOutboxPayloadCodecTest {

    private final JacksonOutboxPayloadCodec jacksonOutboxPayloadCodec = new JacksonOutboxPayloadCodec(new ObjectMapper());

    @Test
    void givenPayloadObject_whenSerializeAndDeserialize_thenReturnOriginalPayload() {
        //given
        final Payload given = new Payload("value-1");

        //when
        final String serialized = this.jacksonOutboxPayloadCodec.serialize(given);
        final Payload actual = this.jacksonOutboxPayloadCodec.deserialize(serialized, Payload.class);

        //then
        assertThat(actual).isEqualTo(given);
    }

    @Test
    void givenRawPayloadString_whenSerialize_thenReturnRawString() {
        //given
        final String given = "{\"event\":\"EMAIL_VERIFY\"}";

        //when
        final String actual = this.jacksonOutboxPayloadCodec.serialize(given);

        //then
        assertThat(actual).isEqualTo(given);
    }

    @Test
    void givenRawPayloadString_whenDeserializeToString_thenReturnRawString() {
        //given
        final String given = "{\"event\":\"EMAIL_VERIFY\"}";

        //when
        final String actual = this.jacksonOutboxPayloadCodec.deserialize(given, String.class);

        //then
        assertThat(actual).isEqualTo(given);
    }

    private record Payload(String value) {
    }
}
