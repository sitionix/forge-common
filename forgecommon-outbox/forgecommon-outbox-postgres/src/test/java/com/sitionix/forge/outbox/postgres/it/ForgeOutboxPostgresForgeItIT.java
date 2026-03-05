package com.sitionix.forge.outbox.postgres.it;

import com.sitionix.forge.outbox.core.model.Event;
import com.sitionix.forge.outbox.core.model.OutboxDispatchSummary;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.ForgeOutbox;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.ForgeTypedOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxWorker;
import com.sitionix.forge.outbox.postgres.it.infra.ForgeOutboxAggregateTypeEntity;
import com.sitionix.forge.outbox.postgres.entity.ForgeOutboxEventEntity;
import com.sitionix.forge.outbox.postgres.it.infra.TestManager;
import com.sitionix.forgeit.core.test.IntegrationTest;
import com.sitionix.forgeit.domain.contract.DbContractsDsl;
import com.sitionix.forgeit.domain.contract.clean.CleanupPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest(properties = {
        "forge.outbox.domain-store=POSTGRES",
        "forge.outbox.worker.enabled=false",
        "forge.outbox.cleanup.enabled=false",
        "forge.outbox.worker.batch-size=1",
        "forge.outbox.worker.retry-delay=PT0S",
        "forge.outbox.worker.max-retries=2"
})
@Import(ForgeOutboxPostgresForgeItIT.TestConfig.class)
class ForgeOutboxPostgresForgeItIT {

    @Autowired
    private ForgeOutbox<ForgeOutboxPayload> forgeOutbox;

    @Autowired
    private ForgeOutboxWorker forgeOutboxWorker;

    @Autowired
    private TestManager testManager;

    @BeforeEach
    void setUp() {
        this.testManager.postgresql()
                .clearAllData(List.of(
                        DbContractsDsl.entity(ForgeOutboxEventEntity.class)
                                .cleanupPolicy(CleanupPolicy.DELETE_ALL)
                                .build()));
        this.testManager.postgresql()
                .clearAllData(List.of(
                        DbContractsDsl.entity(ForgeOutboxAggregateTypeEntity.class)
                                .cleanupPolicy(CleanupPolicy.DELETE_ALL)
                                .build()));
        SuccessPublisher.PUBLISHED_EVENT_TYPES.clear();
    }

    @Test
    void givenIntegrationContext_whenStarted_thenOutboxBeansAvailable() {
        //given

        //when

        //then
        assertThat(this.forgeOutbox).isNotNull();
        assertThat(this.forgeOutboxWorker).isNotNull();
        assertThat(this.testManager).isNotNull();
    }

    @Test
    void givenSupportedPayload_whenDispatchPendingEvents_thenRecordMarkedSentAndPublished() {
        //given
        this.forgeOutbox.send(new SuccessPayload("ok-1"));

        //when
        final OutboxDispatchSummary summary = this.forgeOutboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(1);
        assertThat(summary.getSent()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).containsExactly("EMAIL_VERIFY");
        this.testManager.postgresql().get(ForgeOutboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getEventType(), "EMAIL_VERIFY"))
                .andExpected(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.SENT.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 0))
                .assertEntity();
    }

    @Test
    void givenUnsupportedEventType_whenDispatchPendingEvents_thenRecordMarkedFailed() {
        //given
        this.forgeOutbox.send(new UnsupportedPayload("unknown-1"));

        //when
        final OutboxDispatchSummary summary = this.forgeOutboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(1);
        assertThat(summary.getSent()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).isEmpty();
        this.testManager.postgresql().get(ForgeOutboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getEventType(), "EMAIL_UNKNOWN"))
                .andExpected(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.FAILED.getId()))
                .assertEntity();
    }

    @Test
    void givenPublisherFailure_whenDispatchPendingEvents_thenRecordMarkedFailed() {
        //given
        this.forgeOutbox.send(new FailingPayload("fail-1"));

        //when
        final OutboxDispatchSummary summary = this.forgeOutboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(1);
        assertThat(summary.getSent()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(1);
        this.testManager.postgresql().get(ForgeOutboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getEventType(), "EMAIL_FAIL"))
                .andExpected(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.FAILED.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 1))
                .andExpected(entity -> Objects.nonNull(entity.getLastError()) && entity.getLastError().contains("Forced publish failure"))
                .assertEntity();
    }

    @Test
    void givenSameFailingEventDispatchedTwice_whenRetryLimitReached_thenRecordMarkedDead() {
        //given
        this.forgeOutbox.send(new FailingPayload("fail-2"));

        //when
        final OutboxDispatchSummary firstSummary = this.forgeOutboxWorker.dispatchPendingEvents();
        final OutboxDispatchSummary secondSummary = this.forgeOutboxWorker.dispatchPendingEvents();

        //then
        assertThat(firstSummary.getClaimed()).isEqualTo(1);
        assertThat(firstSummary.getSent()).isEqualTo(0);
        assertThat(firstSummary.getFailed()).isEqualTo(1);
        assertThat(secondSummary.getClaimed()).isEqualTo(1);
        assertThat(secondSummary.getSent()).isEqualTo(0);
        assertThat(secondSummary.getFailed()).isEqualTo(1);
        this.testManager.postgresql().get(ForgeOutboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.DEAD.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 2))
                .assertEntity();
    }

    @Test
    void givenBatchSizeOne_whenDispatchPendingEvents_thenProcessOneRecordPerRun() {
        //given
        this.forgeOutbox.send(new SuccessPayload("batch-1"));
        this.forgeOutbox.send(new SuccessPayload("batch-2"));

        //when
        final OutboxDispatchSummary firstSummary = this.forgeOutboxWorker.dispatchPendingEvents();
        final List<ForgeOutboxEventEntity> afterFirstDispatch = this.testManager.postgresql()
                .get(ForgeOutboxEventEntity.class)
                .getAll();
        final OutboxDispatchSummary secondSummary = this.forgeOutboxWorker.dispatchPendingEvents();
        final List<ForgeOutboxEventEntity> afterSecondDispatch = this.testManager.postgresql()
                .get(ForgeOutboxEventEntity.class)
                .getAll();

        //then
        assertThat(firstSummary.getClaimed()).isEqualTo(1);
        assertThat(firstSummary.getSent()).isEqualTo(1);
        assertThat(firstSummary.getFailed()).isEqualTo(0);
        assertThat(secondSummary.getClaimed()).isEqualTo(1);
        assertThat(secondSummary.getSent()).isEqualTo(1);
        assertThat(secondSummary.getFailed()).isEqualTo(0);
        final long sentAfterFirstDispatch = afterFirstDispatch.stream()
                .filter(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.SENT.getId()))
                .count();
        final long pendingAfterFirstDispatch = afterFirstDispatch.stream()
                .filter(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.PENDING.getId()))
                .count();
        assertThat(sentAfterFirstDispatch).isEqualTo(1);
        assertThat(pendingAfterFirstDispatch).isEqualTo(1);

        final long sentAfterSecondDispatch = afterSecondDispatch.stream()
                .filter(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.SENT.getId()))
                .count();
        assertThat(sentAfterSecondDispatch).isEqualTo(2);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).hasSize(2);
    }

    @Test
    void givenConcurrentWorkers_whenDispatchPendingEvents_thenPublishEventOnlyOnce() throws ExecutionException, InterruptedException {
        //given
        this.forgeOutbox.send(new SuccessPayload("concurrent-1"));
        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        //when
        final Future<OutboxDispatchSummary> firstFuture = executorService.submit(this.forgeOutboxWorker::dispatchPendingEvents);
        final Future<OutboxDispatchSummary> secondFuture = executorService.submit(this.forgeOutboxWorker::dispatchPendingEvents);
        final OutboxDispatchSummary firstSummary;
        final OutboxDispatchSummary secondSummary;
        try {
            firstSummary = firstFuture.get();
            secondSummary = secondFuture.get();
        } finally {
            executorService.shutdown();
        }

        //then
        final int claimed = firstSummary.getClaimed() + secondSummary.getClaimed();
        final int sent = firstSummary.getSent() + secondSummary.getSent();
        final int failed = firstSummary.getFailed() + secondSummary.getFailed();
        assertThat(claimed).isEqualTo(1);
        assertThat(sent).isEqualTo(1);
        assertThat(failed).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).hasSize(1);
        this.testManager.postgresql().get(ForgeOutboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getStatusId(), OutboxStatus.SENT.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 0))
                .assertEntity();
    }

    @Test
    void givenCustomAggregateTypePayload_whenDispatchPendingEvents_thenPersistAggregateTypeDynamically() {
        //given
        this.forgeOutbox.send(new AggregatePayload("site-1"));

        //when
        final OutboxDispatchSummary summary = this.forgeOutboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(1);
        assertThat(summary.getSent()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(0);
        final List<ForgeOutboxEventEntity> outboxEvents = this.testManager.postgresql().get(ForgeOutboxEventEntity.class).getAll();
        final List<ForgeOutboxAggregateTypeEntity> aggregateTypes = this.testManager.postgresql()
                .get(ForgeOutboxAggregateTypeEntity.class)
                .getAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getAggregateTypeId()).isNotNull();
        assertThat(aggregateTypes).anyMatch(entity -> Objects.equals(entity.getId(), outboxEvents.getFirst().getAggregateTypeId())
                && Objects.equals(entity.getDescription(), "SITE"));
    }

    @Test
    void givenNoSupportedPendingEvents_whenDispatchPendingEvents_thenReturnEmptySummary() {
        //given

        //when
        final OutboxDispatchSummary summary = this.forgeOutboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(0);
        assertThat(summary.getSent()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).isEmpty();
        this.testManager.postgresql().get(ForgeOutboxEventEntity.class)
                .hasSize(0);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ForgeOutboxEventPublisher successPublisher() {
            return new SuccessPublisher();
        }

        @Bean
        ForgeOutboxEventPublisher failingPublisher() {
            return new FailingPublisher();
        }

        @Bean
        ForgeOutboxEventPublisher aggregatePublisher() {
            return new AggregatePublisher();
        }
    }

    static class SuccessPublisher extends ForgeTypedOutboxEventPublisher<SuccessPayload> {

        static final List<String> PUBLISHED_EVENT_TYPES = new CopyOnWriteArrayList<>();

        @Override
        protected Class<SuccessPayload> payloadClass() {
            return SuccessPayload.class;
        }

        @Override
        protected void publish(final Event<SuccessPayload> event) {
            PUBLISHED_EVENT_TYPES.add(event.getEventType());
        }
    }

    static class FailingPublisher extends ForgeTypedOutboxEventPublisher<FailingPayload> {

        @Override
        protected Class<FailingPayload> payloadClass() {
            return FailingPayload.class;
        }

        @Override
        protected void publish(final Event<FailingPayload> event) {
            throw new IllegalStateException("Forced publish failure");
        }
    }

    static class AggregatePublisher extends ForgeTypedOutboxEventPublisher<AggregatePayload> {

        @Override
        protected Class<AggregatePayload> payloadClass() {
            return AggregatePayload.class;
        }

        @Override
        protected void publish(final Event<AggregatePayload> event) {
            // no-op
        }
    }

    private record SuccessPayload(String value) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_VERIFY";
        }
    }

    private record FailingPayload(String value) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_FAIL";
        }
    }

    private record UnsupportedPayload(String value) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_UNKNOWN";
        }
    }

    private record AggregatePayload(String value) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_AGGREGATE";
        }

        @Override
        public String aggregateTypeValue() {
            return "SITE";
        }

        @Override
        public Long aggregateId() {
            return 501L;
        }
    }
}
