package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.Event;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

public interface ForgeOutboxEventPublisher<P extends ForgeOutboxPayload> {

    default String eventType() {
        final Class<P> payloadType = this.payloadType();
        final String eventType = this.resolveEventType(payloadType);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalStateException(
                    "Unable to resolve event type for payload " + payloadType.getName()
                            + ". Override eventType() or expose getEventType() via EventMetadataContract.");
        }
        return eventType;
    }

    @SuppressWarnings("unchecked")
    default Class<P> payloadType() {
        final Class<?> payloadType = this.resolvePayloadType(this.getClass());
        if (payloadType == null) {
            throw new IllegalStateException(
                    "Unable to resolve payload type for publisher " + this.getClass().getName()
                            + ". Override payloadType().");
        }
        return (Class<P>) payloadType;
    }

    void publish(Event<P> event) throws Exception;

    private String resolveEventType(final Class<P> payloadType) {
        try {
            final Object eventType = payloadType.getField("EVENT_TYPE").get(null);
            if (eventType instanceof String value && !value.isBlank()) {
                return value;
            }
        } catch (final NoSuchFieldException | IllegalAccessException ignored) {
        }
        if (!EventMetadataContract.class.isAssignableFrom(payloadType)) {
            return null;
        }
        try {
            final EventMetadataContract payload = (EventMetadataContract) payloadType.getDeclaredConstructor().newInstance();
            final String eventType = payload.getEventType();
            return eventType == null || eventType.isBlank() ? null : eventType;
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Class<?> resolvePayloadType(final Class<?> current) {
        Class<?> type = current;
        while (type != null && !Object.class.equals(type)) {
            final Class<?> fromInterfaces = this.resolvePayloadTypeFromTypes(type.getGenericInterfaces());
            if (fromInterfaces != null) {
                return fromInterfaces;
            }
            final Class<?> fromSuperclass = this.resolvePayloadTypeFromType(type.getGenericSuperclass());
            if (fromSuperclass != null) {
                return fromSuperclass;
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private Class<?> resolvePayloadTypeFromTypes(final Type[] types) {
        for (final Type type : types) {
            final Class<?> payloadType = this.resolvePayloadTypeFromType(type);
            if (payloadType != null) {
                return payloadType;
            }
        }
        return null;
    }

    private Class<?> resolvePayloadTypeFromType(final Type type) {
        if (type == null) {
            return null;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            final Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass && ForgeOutboxEventPublisher.class.equals(rawClass)) {
                return this.toClass(parameterizedType.getActualTypeArguments()[0]);
            }
            if (rawType instanceof Class<?> rawClass) {
                final Class<?> nested = this.resolvePayloadType(rawClass);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (type instanceof Class<?> rawClass) {
            return this.resolvePayloadType(rawClass);
        }
        return null;
    }

    private Class<?> toClass(final Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            final Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
            return null;
        }
        if (type instanceof WildcardType wildcardType) {
            final Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length == 0) {
                return null;
            }
            return this.toClass(upperBounds[0]);
        }
        if (type instanceof TypeVariable<?>) {
            return null;
        }
        return null;
    }
}
