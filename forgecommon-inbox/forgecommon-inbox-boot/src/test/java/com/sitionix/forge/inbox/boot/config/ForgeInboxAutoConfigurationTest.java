package com.sitionix.forge.inbox.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import com.sitionix.forge.inbox.core.port.InboxHandler;
import com.sitionix.forge.inbox.core.model.InboxEvent;
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
    void givenInboxStorageWithoutPublishers_whenContextLoads_thenCreateDispatchingChainWithEmptyPublishers() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);

        //when
        //then
        this.contextRunner
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeInbox.class);
                    assertThat(context).hasSingleBean(InboxHandler.class);
                    assertThat(context).hasSingleBean(InboxDispatcher.class);
                    assertThat(context).hasSingleBean(ForgeInboxWorker.class);
                });
    }

    @Test
    void givenInboxStorageAndPublisher_whenContextLoads_thenCreateDispatchingChain() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);
        final ForgeInboxEventHandler<?> publisher = new TestPublisher();

        //when
        //then
        this.contextRunner
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("testPublisher", ForgeInboxEventHandler.class, () -> publisher)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeInbox.class);
                    assertThat(context).hasSingleBean(InboxHandler.class);
                    assertThat(context).hasSingleBean(InboxDispatcher.class);
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
    void givenInboxDisabled_whenContextLoads_thenSkipInboxGraph() {
        //given
        final InboxStorage inboxStorage = mock(InboxStorage.class);
        final ForgeInboxEventHandler<?> publisher = new TestPublisher();

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.enabled=false")
                .withBean(InboxStorage.class, () -> inboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("testPublisher", ForgeInboxEventHandler.class, () -> publisher)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForgeInbox.class);
                    assertThat(context).doesNotHaveBean(InboxHandler.class);
                    assertThat(context).doesNotHaveBean(InboxDispatcher.class);
                });
    }

    private static class TestPublisher implements ForgeInboxEventHandler<TestPayload> {

        @Override
        public Class<TestPayload> payloadClass() {
            return TestPayload.class;
        }

        @Override
        public void handle(final InboxEvent<TestPayload> event) {
            // no-op
        }
    }

    private record TestPayload(String value) implements ForgeInboxPayload {

        @Override
        public String eventType() {
            return "TEST_EVENT";
        }
    }
}
