package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeOutboxPublisherTest {

    private CompositeOutboxPublisher compositeOutboxPublisher;

    @Mock
    private OutboxPayloadCodec outboxPayloadCodec;

    @Mock
    private ForgeOutboxEventPublisher<?> firstPublisher;

    @Mock
    private ForgeOutboxEventPublisher<?> secondPublisher;

    @BeforeEach
    void setUp() {
        this.compositeOutboxPublisher = new CompositeOutboxPublisher(
                List.of(this.firstPublisher, this.secondPublisher),
                this.outboxPayloadCodec);
        clearInvocations(this.firstPublisher, this.secondPublisher, this.outboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.outboxPayloadCodec, this.firstPublisher, this.secondPublisher);
    }

    @Test
    void givenCompositePublisher_whenSupportedEventTypes_thenReturnWildcard() {
        //given

        //when
        final Set<String> actual = this.compositeOutboxPublisher.supportedEventTypes();

        //then
        assertThat(actual).isEqualTo(Set.of("*"));
    }

    @Test
    void givenFirstPublisherHandlesRecord_whenPublish_thenStopIteration() throws Exception {
        //given
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .id("15")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-1\"}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.firstPublisher.tryPublish(outboxRecord, this.outboxPayloadCodec)).thenReturn(true);

        //when
        this.compositeOutboxPublisher.publish(outboxRecord);

        //then
        verify(this.firstPublisher).tryPublish(outboxRecord, this.outboxPayloadCodec);
    }

    @Test
    void givenFirstPublisherSkipsAndSecondHandlesRecord_whenPublish_thenPublishWithSecond() throws Exception {
        //given
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .id("16")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-2\"}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.firstPublisher.tryPublish(outboxRecord, this.outboxPayloadCodec)).thenReturn(false);
        when(this.secondPublisher.tryPublish(outboxRecord, this.outboxPayloadCodec)).thenReturn(true);

        //when
        this.compositeOutboxPublisher.publish(outboxRecord);

        //then
        verify(this.firstPublisher).tryPublish(outboxRecord, this.outboxPayloadCodec);
        verify(this.secondPublisher).tryPublish(outboxRecord, this.outboxPayloadCodec);
    }

    @Test
    void givenNoPublisherHandlesRecord_whenPublish_thenThrowException() throws Exception {
        //given
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .id("17")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-3\"}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.firstPublisher.tryPublish(outboxRecord, this.outboxPayloadCodec)).thenReturn(false);
        when(this.secondPublisher.tryPublish(outboxRecord, this.outboxPayloadCodec)).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> this.compositeOutboxPublisher.publish(outboxRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No ForgeOutboxEventPublisher handled record eventType: EMAIL_VERIFY");
        verify(this.firstPublisher).tryPublish(outboxRecord, this.outboxPayloadCodec);
        verify(this.secondPublisher).tryPublish(outboxRecord, this.outboxPayloadCodec);
    }
}
