package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxAggregateType;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
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
        final SendPayload payload = new SendPayload("EMAIL_VERIFY",
                "trace-1",
                10L,
                Instant.parse("2026-01-01T10:01:00Z"),
                Map.of("header-1", "value-1"),
                Map.of("meta-1", "value-1"));
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
        assertThat(actual.getAggregateType()).isEqualTo("USER");
        assertThat(actual.getAggregateId()).isEqualTo(10L);
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
    }

    @Test
    void givenPayloadWithoutOptionalFields_whenSend_thenApplyDefaults() {
        //given
        final SendPayload payload = new SendPayload("EMAIL_VERIFY",
                null,
                null,
                null,
                null,
                null);
        final ArgumentCaptor<OutboxRecord> argumentCaptor = ArgumentCaptor.forClass(OutboxRecord.class);
        when(this.outboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeOutbox.send(payload);

        //then
        verify(this.outboxPayloadCodec).serialize(payload);
        verify(this.outboxStorage).enqueue(argumentCaptor.capture());
        final OutboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getAggregateType()).isNull();
        assertThat(actual.getAggregateId()).isNull();
        assertThat(actual.getInitiatorType()).isNull();
        assertThat(actual.getHeaders()).isEqualTo(Map.of());
        assertThat(actual.getMetadata()).isEqualTo(Map.of());
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenMissingEventTypePayload_whenSend_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload(" ",
                null,
                null,
                null,
                null,
                null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeOutbox.send(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Outbox eventType is required");
    }

    @Test
    void givenNullPayload_whenSend_thenThrowException() {
        //given
        //when
        //then
        assertThatThrownBy(() -> this.forgeOutbox.send(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Outbox payload is required");
    }

    @Test
    void givenCodecMissing_whenSend_thenThrowException() {
        //given
        final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        final DefaultForgeOutbox<ForgeOutboxPayload> outboxWithoutCodec =
                new DefaultForgeOutbox<>(this.outboxStorage, fixedClock);
        final SendPayload payload = new SendPayload("EMAIL_VERIFY",
                "trace-1",
                14L,
                null,
                null,
                null);

        //when
        //then
        assertThatThrownBy(() -> outboxWithoutCodec.send(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbox payload codec is required for object payload");
    }

    private record SendPayload(String eventType,
                               String traceId,
                               Long userId,
                               Instant nextAttemptAt,
                               Map<String, String> headers,
                               Map<String, String> metadata) implements ForgeOutboxPayload {

        @Override
        public String getOutboxEventType() {
            return this.eventType;
        }

        @Override
        public String getOutboxTraceId() {
            return this.traceId;
        }

        @Override
        public OutboxAggregateType getAgregateType() {
            return OutboxAggregateType.USER;
        }

        @Override
        public Long getAgregateId() {
            return this.userId;
        }

        @Override
        public String getOutboxInitiatorType() {
            return "SYSTEM";
        }

        @Override
        public String getOutboxInitiatorId() {
            return "1";
        }

        @Override
        public Instant getOutboxNextAttemptAt() {
            return this.nextAttemptAt;
        }

        @Override
        public Map<String, String> getOutboxHeaders() {
            return this.headers;
        }

        @Override
        public Map<String, String> getOutboxMetadata() {
            return this.metadata;
        }
    }
}
