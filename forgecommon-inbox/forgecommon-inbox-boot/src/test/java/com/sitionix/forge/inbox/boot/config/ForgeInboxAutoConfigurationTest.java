package com.sitionix.forge.inbox.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.inbox.boot.service.SpringEnumInboxHandler;
import com.sitionix.forge.inbox.boot.worker.ScheduledInboxCleanup;
import com.sitionix.forge.inbox.core.model.EnumForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventType;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import com.sitionix.forge.inbox.core.port.InboxHandler;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import com.sitionix.forge.inbox.core.service.InboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ForgeInboxAutoConfigurationTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ForgeInboxAutoConfiguration.class));
    }

    @Test
    void givenInboxStorageWithoutEventTypes_whenContextLoads_thenFailContext() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);

        //when
        //then
        this.contextRunner
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("no ForgeInboxEventTypes bean is configured");
                });
    }

    @Test
    void givenInboxStorageAndEventTypeRegistry_whenContextLoads_thenCreateDispatchingChain() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);
        final ForgeInboxEventHandler<?> handler = new TestPayloadHandler();
        final ForgeInboxEventTypes eventTypes = new EnumForgeInboxEventTypes<>(TestEventType.class);

        //when
        //then
        this.contextRunner
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("testPayloadHandler", ForgeInboxEventHandler.class, () -> handler)
                .withBean(ForgeInboxEventTypes.class, () -> eventTypes)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeInbox.class);
                    assertThat(context).hasSingleBean(InboxHandler.class);
                    assertThat(context.getBean(InboxHandler.class)).isInstanceOf(SpringEnumInboxHandler.class);
                    assertThat(context).hasSingleBean(InboxDispatcher.class);
                    assertThat(context).hasSingleBean(ForgeInboxWorker.class);
                });
    }

    @Test
    void givenDuplicateTypedHandlers_whenContextLoads_thenFailContext() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);
        final ForgeInboxEventTypes eventTypes = new EnumForgeInboxEventTypes<>(TestEventType.class);

        //when
        //then
        this.contextRunner
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("firstTestPayloadHandler", ForgeInboxEventHandler.class, TestPayloadHandler::new)
                .withBean("secondTestPayloadHandler", ForgeInboxEventHandler.class, TestPayloadHandler::new)
                .withBean(ForgeInboxEventTypes.class, () -> eventTypes)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("expected single matching bean but found 2");
                });
    }

    @Test
    void givenStorageMissing_whenContextLoads_thenSkipForgeInboxGraph() {
        //given

        //when
        //then
        this.contextRunner
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForgeInbox.class);
                    assertThat(context).doesNotHaveBean(InboxHandler.class);
                    assertThat(context).doesNotHaveBean(InboxDispatcher.class);
                });
    }

    @Test
    void givenInboxStorageWithoutEventTypesAndWorkerDisabled_whenContextLoads_thenCreateReceiveOnlyGraph() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.worker.enabled=false")
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ForgeInbox.class);
                    assertThat(context).doesNotHaveBean(InboxHandler.class);
                    assertThat(context).doesNotHaveBean(InboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(ForgeInboxWorker.class);
                    assertThat(context).doesNotHaveBean(ScheduledInboxCleanup.class);
                });
    }

    @Test
    void givenInboxDisabled_whenContextLoads_thenSkipInboxGraph() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.enabled=false")
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForgeInbox.class);
                    assertThat(context).doesNotHaveBean(InboxHandler.class);
                    assertThat(context).doesNotHaveBean(InboxDispatcher.class);
                });
    }

    private static class TestPayloadHandler implements ForgeInboxEventHandler<TestPayload> {

        @Override
        public void handle(final InboxEvent<TestPayload> event) {
            // no-op
        }
    }

    private enum TestEventType implements ForgeInboxEventType {
        TEST(1L, "TEST", TestPayload.class);

        private final Long id;
        private final String description;
        private final Class<?> payloadClass;

        TestEventType(final Long id,
                      final String description,
                      final Class<?> payloadClass) {
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
        public Class<?> payloadClass() {
            return this.payloadClass;
        }
    }

    private record TestPayload(String value) {
    }
}
