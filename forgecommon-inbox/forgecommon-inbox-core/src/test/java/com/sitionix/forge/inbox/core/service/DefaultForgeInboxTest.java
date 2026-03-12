package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import com.sitionix.forge.inbox.core.port.InboxReceiveMetadata;
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

    private DefaultForgeInbox<SendPayload> forgeInbox;

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
    void givenPayloadAndEnvelopeMetadata_whenReceive_thenPersistPendingRecord() {
        //given
        final SendPayload payload = new SendPayload("value-1");
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata(
                "EMAIL_VERIFY",
                "idemp-1",
                "trace-from-metadata",
                Map.of("header-1", "value-1"),
                Map.of("meta-1", "value-1"),
                "SITE",
                10L,
                "SYSTEM",
                "1",
                Instant.parse("2026-01-01T10:01:00Z"));
        final ArgumentCaptor<InboxRecord> argumentCaptor = ArgumentCaptor.forClass(InboxRecord.class);
        when(this.inboxPayloadCodec.serialize(payload)).thenReturn("{\"value\":1}");

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
    void givenMetadataWithoutOptionalFields_whenReceive_thenApplyDefaults() {
        //given
        final SendPayload payload = new SendPayload("value-2");
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-2", null);
        final ArgumentCaptor<InboxRecord> argumentCaptor = ArgumentCaptor.forClass(InboxRecord.class);
        when(this.inboxPayloadCodec.serialize(payload)).thenReturn("{\"value\":2}");

        //when
        this.forgeInbox.receive(payload, receiveMetadata);

        //then
        verify(this.inboxPayloadCodec).serialize(payload);
        verify(this.inboxStorage).enqueue(argumentCaptor.capture());
        final InboxRecord actual = argumentCaptor.getValue();
        assertThat(actual.getAggregateType()).isNull();
        assertThat(actual.getAggregateId()).isNull();
        assertThat(actual.getInitiatorType()).isNull();
        assertThat(actual.getInitiatorId()).isNull();
        assertThat(actual.getHeaders()).isEqualTo(Map.of());
        assertThat(actual.getMetadata()).isEqualTo(Map.of());
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenMissingEventTypeMetadata_whenReceive_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload("value-3");
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata(" ", null, null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(payload, receiveMetadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox eventType is required");
    }

    @Test
    void givenMissingIdempotencyKeyMetadata_whenReceive_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload("value-4");
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", " ", null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(payload, receiveMetadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox idempotencyKey is required");
    }

    @Test
    void givenNullPayload_whenReceive_thenThrowException() {
        //given
        final InboxReceiveMetadata receiveMetadata = new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-3", null);

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(null, receiveMetadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox payload is required");
    }

    @Test
    void givenNullMetadata_whenReceive_thenThrowException() {
        //given
        final SendPayload payload = new SendPayload("value-5");

        //when
        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(payload, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbox metadata is required");
    }

    private record SendPayload(String value) implements ForgeInboxPayload {
    }
}
