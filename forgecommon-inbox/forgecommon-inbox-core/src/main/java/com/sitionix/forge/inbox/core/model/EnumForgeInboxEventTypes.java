package com.sitionix.forge.inbox.core.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Enum-backed implementation of {@link ForgeInboxEventTypes}.
 */
public class EnumForgeInboxEventTypes<E extends Enum<E> & ForgeInboxEventType> implements ForgeInboxEventTypes {

    private final Map<String, E> byDescription;

    public EnumForgeInboxEventTypes(final Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass is required");
        final E[] values = enumClass.getEnumConstants();
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("enumClass has no values: " + enumClass.getName());
        }

        final Map<String, E> mapping = new LinkedHashMap<>();
        Arrays.stream(values).forEach(value -> {
            final String description = this.normalize(value.getDescription());
            if (description == null) {
                throw new IllegalArgumentException("eventType description is required for enum value: " + value.name());
            }
            if (mapping.putIfAbsent(description, value) != null) {
                throw new IllegalArgumentException("duplicate eventType description: " + description);
            }
            if (value.payloadClass() == null) {
                throw new IllegalArgumentException("payloadClass is required for enum value: " + value.name());
            }
        });
        this.byDescription = Map.copyOf(mapping);
    }

    @Override
    public ForgeInboxEventType byDescription(final String description) {
        final String normalized = this.normalize(description);
        if (normalized == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        final E eventType = this.byDescription.get(normalized);
        if (eventType == null) {
            throw new IllegalArgumentException("No inbox event type found for description: " + normalized);
        }
        return eventType;
    }

    @Override
    public Set<String> supportedEventTypes() {
        return this.byDescription.keySet();
    }

    private String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
