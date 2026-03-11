package com.sitionix.forge.inbox.core.model;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Common contract for service-defined inbox reference enums that use id + description.
 */
public interface ForgeInboxTypedEnum {

    Long getId();

    String getDescription();

    static <E extends Enum<E> & ForgeInboxTypedEnum> E fromId(final Class<E> enumClass,
                                                               final Long id) {
        Objects.requireNonNull(enumClass, "enumClass is required");
        return Stream.of(enumClass.getEnumConstants())
                .filter(value -> Objects.equals(value.getId(), id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enum value found for id: " + id));
    }

    static <E extends Enum<E> & ForgeInboxTypedEnum> E fromDescription(final Class<E> enumClass,
                                                                        final String description) {
        Objects.requireNonNull(enumClass, "enumClass is required");
        return Stream.of(enumClass.getEnumConstants())
                .filter(value -> Objects.equals(value.getDescription(), description))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enum value found for description: " + description));
    }
}
