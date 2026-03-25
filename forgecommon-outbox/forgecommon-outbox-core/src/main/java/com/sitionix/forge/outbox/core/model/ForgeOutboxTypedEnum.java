package com.sitionix.forge.outbox.core.model;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Common contract for service-defined outbox reference enums that use id + description.
 */
public interface ForgeOutboxTypedEnum {

    /**
     * @return persistent identifier of enum value
     */
    Long getId();

    /**
     * @return human-readable description used in transport and storage metadata
     */
    String getDescription();

    /**
     * Resolves enum value by id.
     *
     * @param enumClass enum class
     * @param id identifier to resolve
     * @param <E> enum type
     * @return matching enum value
     */
    static <E extends Enum<E> & ForgeOutboxTypedEnum> E fromId(final Class<E> enumClass,
                                                               final Long id) {
        Objects.requireNonNull(enumClass, "enumClass is required");
        return Stream.of(enumClass.getEnumConstants())
                .filter(value -> Objects.equals(value.getId(), id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enum value found for id: " + id));
    }

    /**
     * Resolves enum value by description.
     *
     * @param enumClass enum class
     * @param description description to resolve
     * @param <E> enum type
     * @return matching enum value
     */
    static <E extends Enum<E> & ForgeOutboxTypedEnum> E fromDescription(final Class<E> enumClass,
                                                                        final String description) {
        Objects.requireNonNull(enumClass, "enumClass is required");
        return Stream.of(enumClass.getEnumConstants())
                .filter(value -> Objects.equals(value.getDescription(), description))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enum value found for description: " + description));
    }
}
