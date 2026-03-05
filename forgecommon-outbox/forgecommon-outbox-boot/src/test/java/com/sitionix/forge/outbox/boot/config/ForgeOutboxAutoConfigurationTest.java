package com.sitionix.forge.outbox.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.outbox.core.port.ForgeOutbox;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.ForgeOutboxWorker;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;
import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import com.sitionix.forge.outbox.core.service.OutboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ForgeOutboxAutoConfigurationTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ForgeOutboxAutoConfiguration.class));
    }

    @Test
    void givenOutboxStorageWithoutPublishers_whenContextLoads_thenCreateDispatchingChainWithEmptyPublishers() {
        //given
        final OutboxStorage outboxStorage = mock(OutboxStorage.class);

        //when
        //then
        this.contextRunner
                .withBean(OutboxStorage.class, () -> outboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeOutbox.class);
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context).hasSingleBean(ForgeOutboxWorker.class);
                });
    }

    @Test
    void givenOutboxStorageAndPublisher_whenContextLoads_thenCreateDispatchingChain() {
        //given
        final OutboxStorage outboxStorage = mock(OutboxStorage.class);
        final ForgeOutboxEventPublisher<?> publisher = new TestPublisher();

        //when
        //then
        this.contextRunner
                .withBean(OutboxStorage.class, () -> outboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("testPublisher", ForgeOutboxEventPublisher.class, () -> publisher)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeOutbox.class);
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                });
    }

    @Test
    void givenStorageMissing_whenContextLoads_thenSkipForgeOutboxGraph() {
        //given

        //when
        //then
        this.contextRunner
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForgeOutbox.class);
                    assertThat(context).doesNotHaveBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                });
    }

    @Test
    void givenOutboxDisabled_whenContextLoads_thenSkipOutboxGraph() {
        //given
        final OutboxStorage outboxStorage = mock(OutboxStorage.class);
        final ForgeOutboxEventPublisher<?> publisher = new TestPublisher();

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.outbox.enabled=false")
                .withBean(OutboxStorage.class, () -> outboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("testPublisher", ForgeOutboxEventPublisher.class, () -> publisher)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForgeOutbox.class);
                    assertThat(context).doesNotHaveBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                });
    }

    private static class TestPublisher implements ForgeOutboxEventPublisher<TestPayload> {

        @Override
        public Class<TestPayload> payloadClass() {
            return TestPayload.class;
        }

        @Override
        public void publish(final Event<TestPayload> event) {
            // no-op
        }
    }

    private record TestPayload(String value) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "TEST_EVENT";
        }
    }
}
