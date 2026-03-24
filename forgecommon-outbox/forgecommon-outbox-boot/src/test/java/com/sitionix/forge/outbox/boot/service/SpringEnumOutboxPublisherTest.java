package com.sitionix.forge.outbox.boot.service;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventType;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventTypes;
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
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

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
class SpringEnumOutboxPublisherTest {

    @Mock
    private ForgeOutboxEventTypes eventTypes;

    @Mock
    private ListableBeanFactory beanFactory;

    @Mock
    private OutboxPayloadCodec outboxPayloadCodec;

    @Mock
    private ForgeOutboxEventType eventType;

    @Mock
    private ObjectProvider<ForgeOutboxEventPublisher<TestPayload>> publisherProvider;

    @Mock
    private ForgeOutboxEventPublisher<TestPayload> eventPublisher;

    @Mock
    private TestPayload payload;

    private SpringEnumOutboxPublisher springEnumOutboxPublisher;

    @BeforeEach
    void setUp() {
        this.mockEventTypeRegistry();
        this.springEnumOutboxPublisher = new SpringEnumOutboxPublisher(this.eventTypes, this.beanFactory, this.outboxPayloadCodec);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
                this.eventTypes,
                this.beanFactory,
                this.outboxPayloadCodec,
                this.eventType,
                this.publisherProvider,
                this.eventPublisher);
    }

    @Test
    void givenNormalizedEventType_whenPublish_thenDispatchOutboxEventWithTrimmedEventType() throws Exception {
        //given
        final OutboxRecord outboxRecord = this.getOutboxRecord(" SITE_CREATED ");
        final String payloadJson = outboxRecord.getPayload();
        when(this.outboxPayloadCodec.deserialize(payloadJson, TestPayload.class)).thenReturn(this.payload);
        final ArgumentCaptor<Event<TestPayload>> eventCaptor = ArgumentCaptor.forClass(Event.class);

        //when
        this.springEnumOutboxPublisher.publish(outboxRecord);

        //then
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.publisherProvider).getObject();
        verify(this.outboxPayloadCodec).deserialize(payloadJson, TestPayload.class);
        verify(this.eventPublisher).publish(eventCaptor.capture());

        final Event<TestPayload> actual = eventCaptor.getValue();
        assertThat(actual.getEventType()).isEqualTo("SITE_CREATED");
        assertThat(actual.getId()).isEqualTo("evt-1");
        assertThat(actual.getPayload()).isEqualTo(this.payload);
        assertThat(actual.getIdempotencyId()).isEqualTo(this.resolveIdempotencyId("evt-1", "SITE_CREATED"));
        assertThat(actual.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenUnknownEventType_whenPublish_thenThrowIllegalStateException() {
        //given
        final OutboxRecord outboxRecord = this.getOutboxRecord("SITE_DELETED");

        //when
        //then
        assertThatThrownBy(() -> this.springEnumOutboxPublisher.publish(outboxRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No ForgeOutboxEventType configured for eventType: SITE_DELETED");
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.publisherProvider).getObject();
        verifyNoInteractions(this.outboxPayloadCodec, this.eventPublisher);
    }

    @Test
    void givenBlankEventType_whenPublish_thenThrowIllegalStateException() {
        //given
        final OutboxRecord outboxRecord = this.getOutboxRecord("  ");

        //when
        //then
        assertThatThrownBy(() -> this.springEnumOutboxPublisher.publish(outboxRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("eventType is required");
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.publisherProvider).getObject();
        verifyNoInteractions(this.outboxPayloadCodec, this.eventPublisher);
    }

    @Test
    void givenCreatedPublisher_whenSupportedEventTypes_thenReturnConfiguredTypes() {
        //given
        final Set<String> expected = Set.of("SITE_CREATED");

        //when
        final Set<String> actual = this.springEnumOutboxPublisher.supportedEventTypes();

        //then
        assertThat(actual).isEqualTo(expected);
        verify(this.eventTypes).supportedEventTypes();
        verify(this.eventTypes).byDescription("SITE_CREATED");
        verify(this.eventType).payloadClass();
        verify(this.eventType).getDescription();
        verify(this.beanFactory).getBeanProvider(any(ResolvableType.class));
        verify(this.publisherProvider).getObject();
        verifyNoInteractions(this.outboxPayloadCodec, this.eventPublisher);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockEventTypeRegistry() {
        final Class<? extends ForgeOutboxPayload> payloadClass = (Class<? extends ForgeOutboxPayload>) TestPayload.class;
        when(this.eventTypes.supportedEventTypes()).thenReturn(Set.of("SITE_CREATED"));
        when(this.eventTypes.byDescription(eq("SITE_CREATED"))).thenReturn(this.eventType);
        doReturn(payloadClass).when(this.eventType).payloadClass();
        when(this.beanFactory.getBeanProvider(any(ResolvableType.class))).thenReturn((ObjectProvider) this.publisherProvider);
        when(this.publisherProvider.getObject()).thenReturn(this.eventPublisher);
    }

    private OutboxRecord getOutboxRecord(final String eventType) {
        return OutboxRecord.builder()
                .id("evt-1")
                .eventType(eventType)
                .payload("{\"siteId\":1}")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }

    private UUID resolveIdempotencyId(final String id,
                                      final String eventType) {
        final String value = String.join("|", eventType, id, "2026-01-01T10:00:00Z");
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private interface TestPayload extends ForgeOutboxPayload {
    }
}
