package com.sitionix.forge.outbox.boot.service;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventType;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventTypes;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.ResolvableType;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpringEnumOutboxPublisher implements OutboxPublisher {

    private final Map<String, PublisherBinding> bindingsByEventType;
    private final OutboxPayloadCodec outboxPayloadCodec;

    public SpringEnumOutboxPublisher(final ForgeOutboxEventTypes eventTypes,
                                     final ListableBeanFactory beanFactory,
                                     final OutboxPayloadCodec outboxPayloadCodec) {
        this.bindingsByEventType = this.createBindings(
                Objects.requireNonNull(eventTypes, "eventTypes is required"),
                Objects.requireNonNull(beanFactory, "beanFactory is required"));
        this.outboxPayloadCodec = Objects.requireNonNull(outboxPayloadCodec, "outboxPayloadCodec is required");
    }

    @Override
    public Set<String> supportedEventTypes() {
        return this.bindingsByEventType.keySet();
    }

    @Override
    public void publish(final OutboxRecord record) throws Exception {
        Objects.requireNonNull(record, "record is required");
        final String eventType = this.normalize(record.getEventType());
        if (eventType == null) {
            throw new IllegalStateException("eventType is required");
        }
        final PublisherBinding binding = this.bindingsByEventType.get(eventType);
        if (binding == null) {
            throw new IllegalStateException("No ForgeOutboxEventType configured for eventType: " + eventType);
        }
        this.dispatch(record, eventType, binding);
    }

    private Map<String, PublisherBinding> createBindings(final ForgeOutboxEventTypes eventTypes,
                                                         final ListableBeanFactory beanFactory) {
        final Set<String> configuredEventTypes = Objects.requireNonNull(
                eventTypes.supportedEventTypes(),
                "supportedEventTypes is required");
        if (configuredEventTypes.isEmpty()) {
            throw new IllegalStateException("ForgeOutboxEventTypes must declare at least one eventType");
        }
        final Map<String, PublisherBinding> bindings = configuredEventTypes.stream()
                .map(this::normalizeConfiguredEventType)
                .collect(Collectors.toMap(
                        eventType -> eventType,
                        eventType -> this.resolveBinding(eventTypes, beanFactory, eventType),
                        (left, right) -> {
                            throw new IllegalStateException("Duplicate outbox publisher binding detected");
                        },
                        LinkedHashMap::new));
        return Map.copyOf(bindings);
    }

    private String normalizeConfiguredEventType(final String configuredEventType) {
        final String eventType = this.normalize(configuredEventType);
        if (eventType == null) {
            throw new IllegalStateException("ForgeOutboxEventTypes contains blank eventType");
        }
        return eventType;
    }

    private PublisherBinding resolveBinding(final ForgeOutboxEventTypes eventTypes,
                                            final ListableBeanFactory beanFactory,
                                            final String eventType) {
        final ForgeOutboxEventType eventTypeConfig = eventTypes.byDescription(eventType);
        final Class<? extends ForgeOutboxPayload> payloadClass = Objects.requireNonNull(
                eventTypeConfig.payloadClass(),
                "payloadClass is required for eventType: " + eventTypeConfig.getDescription());
        final ForgeOutboxEventPublisher<?> publisher = this.resolvePublisher(beanFactory, payloadClass);
        return new PublisherBinding(payloadClass, publisher);
    }

    private ForgeOutboxEventPublisher<?> resolvePublisher(final ListableBeanFactory beanFactory,
                                                          final Class<? extends ForgeOutboxPayload> payloadClass) {
        final ResolvableType publisherType = ResolvableType.forClassWithGenerics(ForgeOutboxEventPublisher.class, payloadClass);
        final Object rawBean = beanFactory.getBeanProvider(publisherType).getObject();
        if (!(rawBean instanceof ForgeOutboxEventPublisher<?> publisher)) {
            throw new IllegalStateException("Resolved bean is not ForgeOutboxEventPublisher for payload class: " + payloadClass.getName());
        }
        return publisher;
    }

    @SuppressWarnings("unchecked")
    private <P extends ForgeOutboxPayload> void dispatch(final OutboxRecord record,
                                                         final String normalizedEventType,
                                                         final PublisherBinding rawBinding) throws Exception {
        final Class<P> payloadClass = (Class<P>) rawBinding.payloadClass();
        final ForgeOutboxEventPublisher<P> publisher = (ForgeOutboxEventPublisher<P>) rawBinding.publisher();
        final P payload = this.outboxPayloadCodec.deserialize(record.getPayload(), payloadClass);

        final Event<P> event = Event.<P>builder()
                .id(record.getId())
                .payload(payload)
                .idempotencyId(this.resolveIdempotencyId(record, normalizedEventType))
                .createdAt(record.getCreatedAt())
                .eventType(normalizedEventType)
                .build();
        publisher.publish(event);
    }

    private UUID resolveIdempotencyId(final OutboxRecord record,
                                      final String eventType) {
        final String value = String.join("|",
                eventType == null ? "" : eventType,
                record.getId() == null ? "" : record.getId(),
                record.getCreatedAt() == null ? "" : record.getCreatedAt().toString());
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PublisherBinding(
            Class<? extends ForgeOutboxPayload> payloadClass,
            ForgeOutboxEventPublisher<?> publisher
    ) {
    }
}
