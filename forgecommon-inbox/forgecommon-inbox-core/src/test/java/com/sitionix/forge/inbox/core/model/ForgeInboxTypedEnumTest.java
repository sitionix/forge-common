package com.sitionix.forge.inbox.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForgeInboxTypedEnumTest {

    @Test
    void givenKnownId_whenFromId_thenResolveEnumValue() {
        //given
        final Long id = 2L;

        //when
        final TestInboxType actual = ForgeInboxTypedEnum.fromId(TestInboxType.class, id);

        //then
        assertThat(actual).isEqualTo(TestInboxType.SITE_UPDATED);
    }

    @Test
    void givenUnknownId_whenFromId_thenThrowIllegalArgumentException() {
        //given
        final Long id = 99L;

        //when
        //then
        assertThatThrownBy(() -> ForgeInboxTypedEnum.fromId(TestInboxType.class, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No enum value found for id: 99");
    }

    @Test
    void givenKnownDescription_whenFromDescription_thenResolveEnumValue() {
        //given
        final String description = "SITE_DELETED";

        //when
        final TestInboxType actual = ForgeInboxTypedEnum.fromDescription(TestInboxType.class, description);

        //then
        assertThat(actual).isEqualTo(TestInboxType.SITE_DELETED);
    }

    @Test
    void givenUnknownDescription_whenFromDescription_thenThrowIllegalArgumentException() {
        //given
        final String description = "SITE_UNKNOWN";

        //when
        //then
        assertThatThrownBy(() -> ForgeInboxTypedEnum.fromDescription(TestInboxType.class, description))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No enum value found for description: SITE_UNKNOWN");
    }

    private enum TestInboxType implements ForgeInboxTypedEnum {
        SITE_CREATED(1L, "SITE_CREATED"),
        SITE_UPDATED(2L, "SITE_UPDATED"),
        SITE_DELETED(3L, "SITE_DELETED");

        private final Long id;
        private final String description;

        TestInboxType(final Long id,
                      final String description) {
            this.id = id;
            this.description = description;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        @Override
        public String getDescription() {
            return this.description;
        }
    }
}
