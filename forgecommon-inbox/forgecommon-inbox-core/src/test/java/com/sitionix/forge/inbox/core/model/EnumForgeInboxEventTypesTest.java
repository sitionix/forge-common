package com.sitionix.forge.inbox.core.model;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumForgeInboxEventTypesTest {

    @Test
    void givenKnownEventTypeDescription_whenResolve_thenReturnEnumValue() {
        //given
        final EnumForgeInboxEventTypes<TestEventType> eventTypes = new EnumForgeInboxEventTypes<>(TestEventType.class);

        //when
        final ForgeInboxEventType actual = eventTypes.byDescription("SITE_UPDATED");

        //then
        assertThat(actual).isEqualTo(TestEventType.SITE_UPDATED);
    }

    @Test
    void givenUnknownEventTypeDescription_whenResolve_thenThrowIllegalArgumentException() {
        //given
        final EnumForgeInboxEventTypes<TestEventType> eventTypes = new EnumForgeInboxEventTypes<>(TestEventType.class);

        //when
        //then
        assertThatThrownBy(() -> eventTypes.byDescription("SITE_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No inbox event type found for description: SITE_UNKNOWN");
    }

    @Test
    void givenEventTypes_whenSupportedEventTypes_thenReturnDescriptionsSet() {
        //given
        final EnumForgeInboxEventTypes<TestEventType> eventTypes = new EnumForgeInboxEventTypes<>(TestEventType.class);

        //when
        final var actual = eventTypes.supportedEventTypes();

        //then
        assertThat(actual).containsExactlyInAnyOrder("SITE_CREATED", "SITE_UPDATED", "SITE_DELETED");
    }

    private enum TestEventType implements ForgeInboxEventType {
        SITE_CREATED(1L, "SITE_CREATED", TestPayload.class),
        SITE_UPDATED(2L, "SITE_UPDATED", TestPayload.class),
        SITE_DELETED(3L, "SITE_DELETED", TestPayload.class);

        private final Long id;
        private final String description;
        private final Class<? extends ForgeInboxPayload> payloadClass;

        TestEventType(final Long id,
                      final String description,
                      final Class<? extends ForgeInboxPayload> payloadClass) {
            this.id = id;
            this.description = description;
            this.payloadClass = payloadClass;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        @Override
        public String getDescription() {
            return this.description;
        }

        @Override
        public Class<? extends ForgeInboxPayload> payloadClass() {
            return this.payloadClass;
        }
    }

    private record TestPayload(String value) implements ForgeInboxPayload {
    }
}
