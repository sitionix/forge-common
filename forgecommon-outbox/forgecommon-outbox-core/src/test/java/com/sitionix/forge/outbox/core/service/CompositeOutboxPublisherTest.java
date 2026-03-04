package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeOutboxPublisherTest {

    private CompositeOutboxPublisher compositeOutboxPublisher;

    @Mock
    private OutboxPayloadCodec outboxPayloadCodec;

    @Mock
    private ForgeOutboxEventPublisher<TestPayload> publisher;

    @BeforeEach
    void setUp() {
        when(this.publisher.eventType()).thenReturn("EMAIL_VERIFY");
        when(this.publisher.payloadType()).thenReturn(TestPayload.class);
        this.compositeOutboxPublisher = new CompositeOutboxPublisher(List.of(this.publisher), this.outboxPayloadCodec);
        clearInvocations(this.publisher, this.outboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.outboxPayloadCodec, this.publisher);
    }

    @Test
    void givenPublisher_whenSupportedEventTypes_thenReturnPayloadTypeEventType() {
        //given

        //when
        final Set<String> actual = this.compositeOutboxPublisher.supportedEventTypes();

        //then
        assertThat(actual).isEqualTo(Set.of("EMAIL_VERIFY"));
    }

    @Test
    void givenOutboxRecord_whenPublish_thenDeserializeAndDelegateTypedEvent() throws Exception {
        //given
        final TestPayload payload = new TestPayload("value-1");
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .id("15")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-1\"}")
                .traceId("trace-1")
                .aggregateType("USER")
                .aggregateId(3L)
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        final ArgumentCaptor<Event<TestPayload>> eventCaptor = ArgumentCaptor.forClass((Class) Event.class);

        when(this.outboxPayloadCodec.deserialize(outboxRecord.getPayload(), TestPayload.class))
                .thenReturn(payload);

        //when
        this.compositeOutboxPublisher.publish(outboxRecord);

        //then
        verify(this.outboxPayloadCodec).deserialize(outboxRecord.getPayload(), TestPayload.class);
        verify(this.publisher).payloadType();
        verify(this.publisher).publish(eventCaptor.capture());
        final Event<TestPayload> actual = eventCaptor.getValue();
        assertThat(actual.getId()).isEqualTo("15");
        assertThat(actual.getPayload()).isEqualTo(payload);
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
        assertThat(actual.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(actual.getIdempotencyId()).isNotNull();
    }

    @Test
    void givenDuplicatePublishers_whenCreate_thenThrowException() {
        //given
        clearInvocations(this.publisher);

        //when
        //then
        assertThatThrownBy(() -> new CompositeOutboxPublisher(List.of(this.publisher, this.publisher), this.outboxPayloadCodec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate ForgeOutboxEventPublisher registration for event type: EMAIL_VERIFY");
        verify(this.publisher, times(2)).eventType();
    }

    @Test
    void givenPublisherWithBlankEventType_whenCreate_thenThrowException() {
        //given
        clearInvocations(this.publisher);
        when(this.publisher.eventType()).thenReturn(" ");

        //when
        //then
        assertThatThrownBy(() -> new CompositeOutboxPublisher(List.of(this.publisher), this.outboxPayloadCodec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ForgeOutboxEventPublisher event type is required");
        verify(this.publisher).eventType();
    }

    @Test
    void givenPublisherWithMissingPayloadType_whenCreate_thenThrowException() {
        //given
        clearInvocations(this.publisher);
        when(this.publisher.payloadType()).thenReturn(null);

        //when
        //then
        assertThatThrownBy(() -> new CompositeOutboxPublisher(List.of(this.publisher), this.outboxPayloadCodec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ForgeOutboxEventPublisher payload type is required");
        verify(this.publisher).eventType();
        verify(this.publisher).payloadType();
    }

    private record TestPayload(String value) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_VERIFY";
        }
    }
}
