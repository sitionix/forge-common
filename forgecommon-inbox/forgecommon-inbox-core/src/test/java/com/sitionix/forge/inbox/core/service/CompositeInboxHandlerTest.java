package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
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
class CompositeInboxHandlerTest {

    private CompositeInboxHandler compositeInboxHandler;

    @Mock
    private InboxPayloadCodec inboxPayloadCodec;

    @Mock
    private ForgeInboxEventHandler<?> firstPublisher;

    @Mock
    private ForgeInboxEventHandler<?> secondPublisher;

    @BeforeEach
    void setUp() {
        this.compositeInboxHandler = new CompositeInboxHandler(
                List.of(this.firstPublisher, this.secondPublisher),
                this.inboxPayloadCodec);
        clearInvocations(this.firstPublisher, this.secondPublisher, this.inboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.inboxPayloadCodec, this.firstPublisher, this.secondPublisher);
    }

    @Test
    void givenCompositePublisher_whenSupportedEventTypes_thenReturnWildcard() {
        //given

        //when
        final Set<String> actual = this.compositeInboxHandler.supportedEventTypes();

        //then
        assertThat(actual).isEqualTo(Set.of("*"));
    }

    @Test
    void givenFirstPublisherHandlesRecord_whenPublish_thenStopIteration() throws Exception {
        //given
        final InboxRecord inboxRecord = InboxRecord.builder()
                .id("15")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-1\"}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.firstPublisher.tryHandle(inboxRecord, this.inboxPayloadCodec)).thenReturn(true);

        //when
        this.compositeInboxHandler.handle(inboxRecord);

        //then
        verify(this.firstPublisher).tryHandle(inboxRecord, this.inboxPayloadCodec);
    }

    @Test
    void givenFirstPublisherSkipsAndSecondHandlesRecord_whenPublish_thenPublishWithSecond() throws Exception {
        //given
        final InboxRecord inboxRecord = InboxRecord.builder()
                .id("16")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-2\"}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.firstPublisher.tryHandle(inboxRecord, this.inboxPayloadCodec)).thenReturn(false);
        when(this.secondPublisher.tryHandle(inboxRecord, this.inboxPayloadCodec)).thenReturn(true);

        //when
        this.compositeInboxHandler.handle(inboxRecord);

        //then
        verify(this.firstPublisher).tryHandle(inboxRecord, this.inboxPayloadCodec);
        verify(this.secondPublisher).tryHandle(inboxRecord, this.inboxPayloadCodec);
    }

    @Test
    void givenNoPublisherHandlesRecord_whenPublish_thenThrowException() throws Exception {
        //given
        final InboxRecord inboxRecord = InboxRecord.builder()
                .id("17")
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":\"value-3\"}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.firstPublisher.tryHandle(inboxRecord, this.inboxPayloadCodec)).thenReturn(false);
        when(this.secondPublisher.tryHandle(inboxRecord, this.inboxPayloadCodec)).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> this.compositeInboxHandler.handle(inboxRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No ForgeInboxEventHandler handled record eventType: EMAIL_VERIFY");
        verify(this.firstPublisher).tryHandle(inboxRecord, this.inboxPayloadCodec);
        verify(this.secondPublisher).tryHandle(inboxRecord, this.inboxPayloadCodec);
    }
}
