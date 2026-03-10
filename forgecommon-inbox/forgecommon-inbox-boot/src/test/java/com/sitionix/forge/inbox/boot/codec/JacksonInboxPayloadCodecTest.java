package com.sitionix.forge.inbox.boot.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private record Payload(String value) {
    }
}
