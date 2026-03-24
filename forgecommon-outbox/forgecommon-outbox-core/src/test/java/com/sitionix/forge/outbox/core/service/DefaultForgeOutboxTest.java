package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxSendMetadata;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultForgeOutboxTest {

    @Mock
    private OutboxStorage outboxStorage;

    @Mock
    private OutboxPayloadCodec outboxPayloadCodec;

    private DefaultForgeOutbox<ForgeOutboxPayload> forgeOutbox;

    @BeforeEach
    void setUp() {
        final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        this.forgeOutbox = new DefaultForgeOutbox<>(this.outboxStorage, fixedClock, this.outboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.outboxStorage, this.outboxPayloadCodec);
    }

    @Test
    void givenPayloadWithAllOutboxFields_whenSend_thenPersistPendingRecord() {
        //given
        final SendPayload payload = new SendPayload("value-1");
        final OutboxSendMetadata metadata = new OutboxSendMetadata("EMAIL_VERIFY",
                "trace-1",
                Map.of("header-1", "value-1"),
                Map.of("meta-1", "value-1"),
                "SITE",
                10L,
                "SYSTEM",
                "1",
                Instant.parse("2026-01-01T10:01:00Z"));
        final ArgumentCaptor<OutboxRecord> argumentCaptor = ArgumentCaptor.forClass(OutboxRecord.class);
        when(this.outboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeOutbox.send(payload, metadata);

        //then
        verify(this.outboxPayloadCodec).serialize(payload);
        verify(this.outboxStorage).enqueue(argumentCaptor.capture());
        final OutboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
        assertThat(actual.getPayload()).isEqualTo("{\"value\":1}");
        assertThat(actual.getHeaders()).isEqualTo(Map.of("header-1", "value-1"));
        assertThat(actual.getMetadata()).isEqualTo(Map.of("meta-1", "value-1"));
        assertThat(actual.getTraceId()).isEqualTo("trace-1");
        assertThat(actual.getAggregateType()).isEqualTo("SITE");
        assertThat(actual.getAggregateId()).isEqualTo(10L);
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
    }

    @Test
    void givenPayloadWithoutOptionalFields_whenSend_thenApplyDefaults() {
        //given
        final SendPayload payload = new SendPayload("value-1");
        final OutboxSendMetadata metadata = new OutboxSendMetadata("EMAIL_VERIFY",
                null,
                null,
                null,
                "   ",
                null,
                "SYSTEM",
                "1",
                null);
        final ArgumentCaptor<OutboxRecord> argumentCaptor = ArgumentCaptor.forClass(OutboxRecord.class);
        when(this.outboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeOutbox.send(payload, metadata);

        //then
        verify(this.outboxPayloadCodec).serialize(payload);
        verify(this.outboxStorage).enqueue(argumentCaptor.capture());
        final OutboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getAggregateType()).isNull();
        assertThat(actual.getAggregateId()).isNull();
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getHeaders()).isEqualTo(Map.of());
        assertThat(actual.getMetadata()).isEqualTo(Map.of());
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenLegacyPayloadMetadata_whenSendWithoutExplicitMetadata_thenPersistPendingRecord() {
        //given
        final LegacyPayload payload = new LegacyPayload();
        final ArgumentCaptor<OutboxRecord> argumentCaptor = ArgumentCaptor.forClass(OutboxRecord.class);
        when(this.outboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeOutbox.send(payload);

        //then
        verify(this.outboxPayloadCodec).serialize(payload);
        verify(this.outboxStorage).enqueue(argumentCaptor.capture());
        final OutboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
        assertThat(actual.getPayload()).isEqualTo("{\"value\":1}");
        assertThat(actual.getHeaders()).isEqualTo(Map.of("header-1", "value-1"));
        assertThat(actual.getMetadata()).isEqualTo(Map.of("meta-1", "value-1"));
        assertThat(actual.getTraceId()).isEqualTo("trace-1");
        assertThat(actual.getAggregateType()).isEqualTo("SITE");
        assertThat(actual.getAggregateId()).isEqualTo(10L);
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
    }

    @Test
    void givenMissingEventTypePayload_whenSend_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload("value-1");
        final OutboxSendMetadata metadata = new OutboxSendMetadata(" ");

        //when
        //then
        assertThatThrownBy(() -> this.forgeOutbox.send(payload, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Outbox eventType is required");
    }

    @Test
    void givenNullPayload_whenSend_thenThrowException() {
        //given
        //when
        //then
        assertThatThrownBy(() -> this.forgeOutbox.send(null, new OutboxSendMetadata("EMAIL_VERIFY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Outbox payload is required");
    }

    @Test
    void givenNullMetadata_whenSend_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload("value-1");

        //when
        //then
        assertThatThrownBy(() -> this.forgeOutbox.send(payload, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Outbox metadata is required");
    }

    private record SendPayload(String value) implements ForgeOutboxPayload {
    }

    private static final class LegacyPayload implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_VERIFY";
        }

        @Override
        public Map<String, String> headers() {
            return Map.of("header-1", "value-1");
        }

        @Override
        public Map<String, String> metadata() {
            return Map.of("meta-1", "value-1");
        }

        @Override
        public String traceId() {
            return "trace-1";
        }

        @Override
        public String aggregateTypeValue() {
            return "SITE";
        }

        @Override
        public Long aggregateId() {
            return 10L;
        }

        @Override
        public String initiatorType() {
            return "SYSTEM";
        }

        @Override
        public String initiatorId() {
            return "1";
        }

        @Override
        public Instant nextAttemptAt() {
            return Instant.parse("2026-01-01T10:01:00Z");
        }
    }
}
