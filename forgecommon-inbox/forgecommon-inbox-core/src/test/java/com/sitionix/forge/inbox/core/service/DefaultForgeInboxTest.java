package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxAggregateType;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.InboxReceiveMetadata;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import com.sitionix.forge.inbox.core.port.InboxStorage;
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
class DefaultForgeInboxTest {

    @Mock
    private InboxStorage inboxStorage;

    @Mock
    private InboxPayloadCodec inboxPayloadCodec;

    private DefaultForgeInbox<ForgeInboxPayload> forgeInbox;

    @BeforeEach
    void setUp() {
        final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        this.forgeInbox = new DefaultForgeInbox<>(this.inboxStorage, fixedClock, this.inboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.inboxStorage, this.inboxPayloadCodec);
    }

    @Test
    void givenPayloadWithAllInboxFields_whenReceive_thenPersistPendingRecord() {
        //given
        final SendPayload payload = new SendPayload(
                "trace-from-payload",
                "SITE",
                10L,
                Instant.parse("2026-01-01T10:01:00Z"),
                Map.of("header-1", "value-1"),
                Map.of("meta-1", "value-1"));
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-1", "trace-from-metadata");
        final ArgumentCaptor<InboxRecord> argumentCaptor = ArgumentCaptor.forClass(InboxRecord.class);
        when(this.inboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeInbox.receive(payload, receiveMetadata);

        //then
        verify(this.inboxPayloadCodec).serialize(payload);
        verify(this.inboxStorage).enqueue(argumentCaptor.capture());
        final InboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
        assertThat(actual.getPayload()).isEqualTo("{\"value\":1}");
        assertThat(actual.getHeaders()).isEqualTo(Map.of("header-1", "value-1"));
        assertThat(actual.getMetadata()).isEqualTo(Map.of("meta-1", "value-1"));
        assertThat(actual.getTraceId()).isEqualTo("trace-from-metadata");
        assertThat(actual.getIdempotencyKey()).isEqualTo("idemp-1");
        assertThat(actual.getAggregateType()).isEqualTo("SITE");
        assertThat(actual.getAggregateId()).isEqualTo(10L);
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
    }

    @Test
    void givenPayloadWithoutOptionalFields_whenReceive_thenApplyDefaults() {
        //given
        final SendPayload payload = new SendPayload(
                null,
                "   ",
                null,
                null,
                null,
                null);
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", null, null);
        final ArgumentCaptor<InboxRecord> argumentCaptor = ArgumentCaptor.forClass(InboxRecord.class);
        when(this.inboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeInbox.receive(payload, receiveMetadata);

        //then
        verify(this.inboxPayloadCodec).serialize(payload);
        verify(this.inboxStorage).enqueue(argumentCaptor.capture());
        final InboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getAggregateType()).isNull();
        assertThat(actual.getAggregateId()).isNull();
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getHeaders()).isEqualTo(Map.of());
        assertThat(actual.getMetadata()).isEqualTo(Map.of());
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenMissingEventTypeMetadata_whenReceive_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload(
                null,
                null,
                null,
                null,
                null,
                null);
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata(" ", null, null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(payload, receiveMetadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox eventType is required");
    }

    @Test
    void givenNullPayload_whenReceive_thenThrowException() {
        //given
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", null, null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(null, receiveMetadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox payload is required");
    }

    @Test
    void givenNullMetadata_whenReceive_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload(
                null,
                null,
                null,
                null,
                null,
                null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(payload, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox metadata is required");
    }

    @Test
    void givenLegacyEnumAggregateTypePayload_whenReceive_thenPersistAggregateType() {
        //given
        final LegacySendPayload payload = new LegacySendPayload(77L);
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", null, null);
        final ArgumentCaptor<InboxRecord> argumentCaptor = ArgumentCaptor.forClass(InboxRecord.class);
        when(this.inboxPayloadCodec.serialize(payload))
                .thenReturn("{\"value\":1}");

        //when
        this.forgeInbox.receive(payload, receiveMetadata);

        //then
        verify(this.inboxPayloadCodec).serialize(payload);
        verify(this.inboxStorage).enqueue(argumentCaptor.capture());
        final InboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getAggregateType()).isEqualTo("USER");
        assertThat(actual.getAggregateId()).isEqualTo(77L);
    }

    private record SendPayload(String traceId,
                               String aggregateTypeName,
                               Long userId,
                               Instant nextAttemptAt,
                               Map<String, String> headers,
                               Map<String, String> metadata) implements ForgeInboxPayload {

        @Override
        public String traceId() {
            return this.traceId;
        }

        @Override
        public String aggregateTypeValue() {
            return this.aggregateTypeName;
        }

        @Override
        public Long aggregateId() {
            return this.userId;
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
            return this.nextAttemptAt;
        }

        @Override
        public Map<String, String> headers() {
            return this.headers;
        }

        @Override
        public Map<String, String> metadata() {
            return this.metadata;
        }
    }

    private record LegacySendPayload(Long userId) implements ForgeInboxPayload {

        @Override
        public InboxAggregateType aggregateType() {
            return InboxAggregateType.USER;
        }

        @Override
        public Long aggregateId() {
            return this.userId;
        }
    }
}
