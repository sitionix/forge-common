package com.sitionix.forge.outbox.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.outbox.core.model.EnumForgeOutboxEventTypes;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventType;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventTypes;
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
    void givenOutboxStorageWithoutEventTypesAndWorkerDisabled_whenContextLoads_thenCreateSendOnlyGraph() {
        //given
        final OutboxStorage outboxStorage = mock(OutboxStorage.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.outbox.worker.enabled=false")
                .withBean(OutboxStorage.class, () -> outboxStorage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeOutbox.class);
                    assertThat(context).doesNotHaveBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(ForgeOutboxWorker.class);
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
                .withBean(ForgeOutboxEventTypes.class, () -> new EnumForgeOutboxEventTypes<>(TestEventType.class))
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
    void givenCustomOutboxPublisherWithoutEventTypes_whenContextLoads_thenCreateDispatchingChain() {
        //given
        final OutboxStorage outboxStorage = mock(OutboxStorage.class);
        final OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);

        //when
        //then
        this.contextRunner
                .withBean(OutboxStorage.class, () -> outboxStorage)
                .withBean(OutboxPublisher.class, () -> outboxPublisher)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(ForgeOutbox.class);
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context).hasSingleBean(ForgeOutboxWorker.class);
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
                .withBean(ForgeOutboxEventTypes.class, () -> new EnumForgeOutboxEventTypes<>(TestEventType.class))
                .withBean("testPublisher", ForgeOutboxEventPublisher.class, () -> publisher)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForgeOutbox.class);
                    assertThat(context).doesNotHaveBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                });
    }

    private static class TestPublisher implements ForgeOutboxEventPublisher<TestPayload> {

        @Override
        public void publish(final Event<TestPayload> event) {
            // no-op
        }
    }

    private record TestPayload(String value) implements ForgeOutboxPayload {
    }

    private enum TestEventType implements ForgeOutboxEventType {
        TEST_EVENT(1L, "TEST_EVENT", TestPayload.class);

        private final Long id;
        private final String description;
        private final Class<? extends ForgeOutboxPayload> payloadClass;

        TestEventType(final Long id,
                      final String description,
                      final Class<? extends ForgeOutboxPayload> payloadClass) {
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
        public Class<? extends ForgeOutboxPayload> payloadClass() {
            return this.payloadClass;
        }
    }
}
