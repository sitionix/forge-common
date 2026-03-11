package com.sitionix.forge.inbox.boot.service;

import com.sitionix.forge.inbox.core.model.ForgeInboxEventType;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.InboxHandler;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.ResolvableType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringEnumInboxHandler implements InboxHandler {

    private final Map<String, HandlerBinding> bindingsByEventType;
    private final InboxPayloadCodec inboxPayloadCodec;

    public SpringEnumInboxHandler(final ForgeInboxEventTypes eventTypes,
                                  final ListableBeanFactory beanFactory,
                                  final InboxPayloadCodec inboxPayloadCodec) {
        this.bindingsByEventType = this.createBindings(
                Objects.requireNonNull(eventTypes, "eventTypes is required"),
                Objects.requireNonNull(beanFactory, "beanFactory is required"));
        this.inboxPayloadCodec = Objects.requireNonNull(inboxPayloadCodec, "inboxPayloadCodec is required");
    }

    @Override
    public Set<String> supportedEventTypes() {
        return this.bindingsByEventType.keySet();
    }

    @Override
    public void handle(final InboxRecord record) throws Exception {
        Objects.requireNonNull(record, "record is required");
        final String eventType = this.normalize(record.getEventType());
        if (eventType == null) {
            throw new IllegalStateException("eventType is required");
        }
        final HandlerBinding binding = this.bindingsByEventType.get(eventType);
        if (binding == null) {
            throw new IllegalStateException("No ForgeInboxEventType configured for eventType: " + eventType);
        }
        this.dispatch(record, binding);
    }

    private Map<String, HandlerBinding> createBindings(final ForgeInboxEventTypes eventTypes,
                                                       final ListableBeanFactory beanFactory) {
        final Set<String> configuredEventTypes = Objects.requireNonNull(
                eventTypes.supportedEventTypes(),
                "supportedEventTypes is required");
        if (configuredEventTypes.isEmpty()) {
            throw new IllegalStateException("ForgeInboxEventTypes must declare at least one eventType");
        }
        final Map<String, HandlerBinding> bindings = configuredEventTypes.stream()
                .map(this::normalizeConfiguredEventType)
                .collect(Collectors.toMap(
                        eventType -> eventType,
                        eventType -> this.resolveBinding(eventTypes, beanFactory, eventType),
                        (left, right) -> {
                            throw new IllegalStateException("Duplicate inbox handler binding detected");
                        },
                        LinkedHashMap::new));
        return Map.copyOf(bindings);
    }

    private String normalizeConfiguredEventType(final String configuredEventType) {
        final String eventType = this.normalize(configuredEventType);
        if (eventType == null) {
            throw new IllegalStateException("ForgeInboxEventTypes contains blank eventType");
        }
        return eventType;
    }

    private HandlerBinding resolveBinding(final ForgeInboxEventTypes eventTypes,
                                          final ListableBeanFactory beanFactory,
                                          final String eventType) {
        final ForgeInboxEventType eventTypeConfig = eventTypes.byDescription(eventType);
        final Class<?> payloadClass = Objects.requireNonNull(
                eventTypeConfig.payloadClass(),
                "payloadClass is required for eventType: " + eventTypeConfig.getDescription());
        final ForgeInboxEventHandler<?> handler = this.resolveHandler(beanFactory, payloadClass);
        return new HandlerBinding(payloadClass, handler);
    }

    private ForgeInboxEventHandler<?> resolveHandler(final ListableBeanFactory beanFactory,
                                                     final Class<?> payloadClass) {
        final ResolvableType handlerType = ResolvableType.forClassWithGenerics(ForgeInboxEventHandler.class, payloadClass);
        final Object rawBean = beanFactory.getBeanProvider(handlerType).getObject();
        if (!(rawBean instanceof ForgeInboxEventHandler<?> handler)) {
            throw new IllegalStateException("Resolved bean is not ForgeInboxEventHandler for payload class: " + payloadClass.getName());
        }
        return handler;
    }

    @SuppressWarnings("unchecked")
    private <P> void dispatch(final InboxRecord record,
                              final HandlerBinding rawBinding) throws Exception {
        final Class<P> payloadClass = (Class<P>) rawBinding.payloadClass();
        final ForgeInboxEventHandler<P> handler = (ForgeInboxEventHandler<P>) rawBinding.handler();
        final P payload = this.inboxPayloadCodec.deserialize(record.getPayload(), payloadClass);

        final InboxEvent<P> event = InboxEvent.<P>builder()
                .id(record.getId())
                .payload(payload)
                .idempotencyKey(record.getIdempotencyKey())
                .createdAt(record.getCreatedAt())
                .eventType(record.getEventType())
                .build();
        handler.handle(event);
    }

    private String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record HandlerBinding(
            Class<?> payloadClass,
            ForgeInboxEventHandler<?> handler
    ) {
    }
}
