package com.sitionix.forge.inbox.boot.service;

import com.sitionix.forge.inbox.core.model.ForgeInboxEventType;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringEnumInboxHandlerTest {

    @Mock
    private ForgeInboxEventTypes eventTypes;

    @Mock
    private ListableBeanFactory beanFactory;

    @Mock
    private InboxPayloadCodec inboxPayloadCodec;

    @Mock
    private ForgeInboxEventType eventType;

    @Mock
    private ObjectProvider<ForgeInboxEventHandler<TestPayload>> handlerProvider;

    @Mock
    private ForgeInboxEventHandler<TestPayload> eventHandler;

    @Mock
    private TestPayload payload;

    private SpringEnumInboxHandler springEnumInboxHandler;

    @BeforeEach
    void setUp() {
        this.mockEventTypeRegistry();
        this.springEnumInboxHandler = new SpringEnumInboxHandler(this.eventTypes, this.beanFactory, this.inboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
                this.eventTypes,
                this.beanFactory,
                this.inboxPayloadCodec,
                this.eventType,
                this.handlerProvider,
                this.eventHandler);
    }

    @Test
    void givenNormalizedEventType_whenHandle_thenDispatchInboxEventWithTrimmedEventType() throws Exception {
        //given
        final InboxRecord inboxRecord = this.getInboxRecord(" SITE_CREATED ");
        final String payloadJson = inboxRecord.getPayload();
        when(this.inboxPayloadCodec.deserialize(payloadJson, TestPayload.class)).thenReturn(this.payload);
        final ArgumentCaptor<InboxEvent<TestPayload>> eventCaptor = ArgumentCaptor.forClass(InboxEvent.class);

        //when
        this.springEnumInboxHandler.handle(inboxRecord);

        //then
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.handlerProvider).getObject();
        verify(this.inboxPayloadCodec).deserialize(payloadJson, TestPayload.class);
        verify(this.eventHandler).handle(eventCaptor.capture());

        final InboxEvent<TestPayload> actual = eventCaptor.getValue();
        assertThat(actual.getEventType()).isEqualTo("SITE_CREATED");
        assertThat(actual.getId()).isEqualTo("evt-1");
        assertThat(actual.getPayload()).isEqualTo(this.payload);
        assertThat(actual.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(actual.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenUnknownEventType_whenHandle_thenThrowIllegalStateException() {
        //given
        final InboxRecord inboxRecord = this.getInboxRecord("SITE_DELETED");

        //when
        //then
        assertThatThrownBy(() -> this.springEnumInboxHandler.handle(inboxRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No ForgeInboxEventType configured for eventType: SITE_DELETED");
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.handlerProvider).getObject();
        verifyNoInteractions(this.inboxPayloadCodec, this.eventHandler);
    }

    @Test
    void givenBlankEventType_whenHandle_thenThrowIllegalStateException() {
        //given
        final InboxRecord inboxRecord = this.getInboxRecord("  ");

        //when
        //then
        assertThatThrownBy(() -> this.springEnumInboxHandler.handle(inboxRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("eventType is required");
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.handlerProvider).getObject();
        verifyNoInteractions(this.inboxPayloadCodec, this.eventHandler);
    }

    @Test
    void givenCreatedHandler_whenSupportedEventTypes_thenReturnConfiguredTypes() {
        //given
        final Set<String> expected = Set.of("SITE_CREATED");

        //when
        final Set<String> actual = this.springEnumInboxHandler.supportedEventTypes();

        //then
        assertThat(actual).isEqualTo(expected);
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.handlerProvider).getObject();
        verifyNoInteractions(this.inboxPayloadCodec, this.eventHandler);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockEventTypeRegistry() {
        final Class<? extends ForgeInboxPayload> payloadClass = (Class<? extends ForgeInboxPayload>) TestPayload.class;
        when(this.eventTypes.supportedEventTypes()).thenReturn(Set.of("SITE_CREATED"));
        when(this.eventTypes.byDescription(eq("SITE_CREATED"))).thenReturn(this.eventType);
        doReturn(payloadClass).when(this.eventType).payloadClass();
        when(this.beanFactory.getBeanProvider(any(ResolvableType.class))).thenReturn((ObjectProvider) this.handlerProvider);
        when(this.handlerProvider.getObject()).thenReturn(this.eventHandler);
    }

    private InboxRecord getInboxRecord(final String eventType) {
        return InboxRecord.builder()
                .id("evt-1")
                .eventType(eventType)
                .payload("{\"siteId\":1}")
                .idempotencyKey("idem-1")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }

    private interface TestPayload extends ForgeInboxPayload {
    }
}
