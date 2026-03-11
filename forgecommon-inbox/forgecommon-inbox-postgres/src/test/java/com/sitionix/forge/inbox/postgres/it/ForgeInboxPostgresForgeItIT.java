package com.sitionix.forge.inbox.postgres.it;

import com.sitionix.forge.inbox.core.model.InboxEvent;
import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;
import com.sitionix.forge.inbox.core.model.EnumForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventType;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import com.sitionix.forge.inbox.core.port.InboxReceiveMetadata;
import com.sitionix.forge.inbox.postgres.entity.ForgeInboxEventEntity;
import com.sitionix.forge.inbox.postgres.it.infra.TestManager;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest(properties = {
        "forge.inbox.domain-store=POSTGRES",
        "forge.inbox.worker.enabled=false",
        "forge.inbox.cleanup.enabled=false",
        "forge.inbox.worker.batch-size=1",
        "forge.inbox.worker.retry-delay=PT0S",
        "forge.inbox.worker.max-retries=2"
})
@Import(ForgeInboxPostgresForgeItIT.TestConfig.class)
class ForgeInboxPostgresForgeItIT {

    @Autowired
    private ForgeInbox<ForgeInboxPayload> forgeInbox;

    @Autowired
    private ForgeInboxWorker forgeInboxWorker;

    @Autowired
    private TestManager testManager;

    @BeforeEach
    void setUp() {
        this.testManager.postgresql()
                .clearAllData(List.of(
                        DbContractsDsl.entity(ForgeInboxEventEntity.class)
                                .cleanupPolicy(CleanupPolicy.DELETE_ALL)
                                .build()));
        SuccessPublisher.PUBLISHED_EVENT_TYPES.clear();
    }

    @Test
    void givenIntegrationContext_whenStarted_thenInboxBeansAvailable() {
        //given

        //when

        //then
        assertThat(this.forgeInbox).isNotNull();
        assertThat(this.forgeInboxWorker).isNotNull();
        assertThat(this.testManager).isNotNull();
    }

    @Test
    void givenSupportedPayload_whenDispatchPendingEvents_thenRecordMarkedSentAndPublished() {
        //given
        this.forgeInbox.receive(new SuccessPayload("ok-1"), new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-ok-1", null));

        //when
        final InboxDispatchSummary summary = this.forgeInboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(1);
        assertThat(summary.getProcessed()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).containsExactly("EMAIL_VERIFY");
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getEventType(), "EMAIL_VERIFY"))
                .andExpected(entity -> Objects.equals(entity.getStatusId(), InboxStatus.PROCESSED.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 0))
                .assertEntity();
    }

    @Test
    void givenUnsupportedEventType_whenDispatchPendingEvents_thenLeaveRecordPending() {
        //given
        this.forgeInbox.receive(new UnsupportedPayload("unknown-1"), new InboxReceiveMetadata("EMAIL_UNKNOWN", "idemp-unknown-1", null));

        //when
        final InboxDispatchSummary summary = this.forgeInboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(0);
        assertThat(summary.getProcessed()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).isEmpty();
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getEventType(), "EMAIL_UNKNOWN"))
                .andExpected(entity -> Objects.equals(entity.getStatusId(), InboxStatus.PENDING.getId()))
                .assertEntity();
    }

    @Test
    void givenPublisherFailure_whenDispatchPendingEvents_thenRecordMarkedFailed() {
        //given
        this.forgeInbox.receive(new FailingPayload("fail-1"), new InboxReceiveMetadata("EMAIL_FAIL", "idemp-fail-1", null));

        //when
        final InboxDispatchSummary summary = this.forgeInboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(1);
        assertThat(summary.getProcessed()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(1);
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getEventType(), "EMAIL_FAIL"))
                .andExpected(entity -> Objects.equals(entity.getStatusId(), InboxStatus.FAILED.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 1))
                .andExpected(entity -> Objects.nonNull(entity.getLastError()) && entity.getLastError().contains("Forced publish failure"))
                .assertEntity();
    }

    @Test
    void givenSameFailingEventDispatchedTwice_whenRetryLimitReached_thenRecordMarkedDead() {
        //given
        this.forgeInbox.receive(new FailingPayload("fail-2"), new InboxReceiveMetadata("EMAIL_FAIL", "idemp-fail-2", null));

        //when
        final InboxDispatchSummary firstSummary = this.forgeInboxWorker.dispatchPendingEvents();
        final InboxDispatchSummary secondSummary = this.forgeInboxWorker.dispatchPendingEvents();

        //then
        assertThat(firstSummary.getClaimed()).isEqualTo(1);
        assertThat(firstSummary.getProcessed()).isEqualTo(0);
        assertThat(firstSummary.getFailed()).isEqualTo(1);
        assertThat(secondSummary.getClaimed()).isEqualTo(1);
        assertThat(secondSummary.getProcessed()).isEqualTo(0);
        assertThat(secondSummary.getFailed()).isEqualTo(1);
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getStatusId(), InboxStatus.DEAD.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 2))
                .assertEntity();
    }

    @Test
    void givenBatchSizeOne_whenDispatchPendingEvents_thenProcessOneRecordPerRun() {
        //given
        this.forgeInbox.receive(new SuccessPayload("batch-1"), new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-batch-1", null));
        this.forgeInbox.receive(new SuccessPayload("batch-2"), new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-batch-2", null));

        //when
        final InboxDispatchSummary firstSummary = this.forgeInboxWorker.dispatchPendingEvents();
        final List<ForgeInboxEventEntity> afterFirstDispatch = this.testManager.postgresql()
                .get(ForgeInboxEventEntity.class)
                .getAll();
        final InboxDispatchSummary secondSummary = this.forgeInboxWorker.dispatchPendingEvents();
        final List<ForgeInboxEventEntity> afterSecondDispatch = this.testManager.postgresql()
                .get(ForgeInboxEventEntity.class)
                .getAll();

        //then
        assertThat(firstSummary.getClaimed()).isEqualTo(1);
        assertThat(firstSummary.getProcessed()).isEqualTo(1);
        assertThat(firstSummary.getFailed()).isEqualTo(0);
        assertThat(secondSummary.getClaimed()).isEqualTo(1);
        assertThat(secondSummary.getProcessed()).isEqualTo(1);
        assertThat(secondSummary.getFailed()).isEqualTo(0);
        final long sentAfterFirstDispatch = afterFirstDispatch.stream()
                .filter(entity -> Objects.equals(entity.getStatusId(), InboxStatus.PROCESSED.getId()))
                .count();
        final long pendingAfterFirstDispatch = afterFirstDispatch.stream()
                .filter(entity -> Objects.equals(entity.getStatusId(), InboxStatus.PENDING.getId()))
                .count();
        assertThat(sentAfterFirstDispatch).isEqualTo(1);
        assertThat(pendingAfterFirstDispatch).isEqualTo(1);

        final long sentAfterSecondDispatch = afterSecondDispatch.stream()
                .filter(entity -> Objects.equals(entity.getStatusId(), InboxStatus.PROCESSED.getId()))
                .count();
        assertThat(sentAfterSecondDispatch).isEqualTo(2);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).hasSize(2);
    }

    @Test
    void givenConcurrentWorkers_whenDispatchPendingEvents_thenPublishEventOnlyOnce() throws ExecutionException, InterruptedException {
        //given
        this.forgeInbox.receive(new SuccessPayload("concurrent-1"), new InboxReceiveMetadata("EMAIL_VERIFY", "idemp-concurrent-1", null));
        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        //when
        final Future<InboxDispatchSummary> firstFuture = executorService.submit(this.forgeInboxWorker::dispatchPendingEvents);
        final Future<InboxDispatchSummary> secondFuture = executorService.submit(this.forgeInboxWorker::dispatchPendingEvents);
        final InboxDispatchSummary firstSummary;
        final InboxDispatchSummary secondSummary;
        try {
            firstSummary = firstFuture.get();
            secondSummary = secondFuture.get();
        } finally {
            executorService.shutdown();
        }

        //then
        final int claimed = firstSummary.getClaimed() + secondSummary.getClaimed();
        final int sent = firstSummary.getProcessed() + secondSummary.getProcessed();
        final int failed = firstSummary.getFailed() + secondSummary.getFailed();
        assertThat(claimed).isEqualTo(1);
        assertThat(sent).isEqualTo(1);
        assertThat(failed).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).hasSize(1);
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(1)
                .singleElement()
                .andExpected(entity -> Objects.equals(entity.getStatusId(), InboxStatus.PROCESSED.getId()))
                .andExpected(entity -> Objects.equals(entity.getRetryCount(), 0))
                .assertEntity();
    }

    @Test
    void givenUnknownAggregateTypePayload_whenSend_thenThrowAndDoNotPersistEvent() {
        //given
        final AggregatePayload payload = new AggregatePayload("site-1");

        //then
        assertThatThrownBy(() -> this.forgeInbox.receive(payload, new InboxReceiveMetadata("EMAIL_AGGREGATE", "idemp-aggregate-1", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown inbox aggregateType 'SITE'");
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(0);
    }

    @Test
    void givenNoSupportedPendingEvents_whenDispatchPendingEvents_thenReturnEmptySummary() {
        //given

        //when
        final InboxDispatchSummary summary = this.forgeInboxWorker.dispatchPendingEvents();

        //then
        assertThat(summary.getClaimed()).isEqualTo(0);
        assertThat(summary.getProcessed()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(SuccessPublisher.PUBLISHED_EVENT_TYPES).isEmpty();
        this.testManager.postgresql().get(ForgeInboxEventEntity.class)
                .hasSize(0);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ForgeInboxEventHandler<?> successPublisher() {
            return new SuccessPublisher();
        }

        @Bean
        ForgeInboxEventHandler<?> failingPublisher() {
            return new FailingPublisher();
        }

        @Bean
        ForgeInboxEventTypes eventTypes() {
            return new EnumForgeInboxEventTypes<>(TestEventType.class);
        }
    }

    static class SuccessPublisher implements ForgeInboxEventHandler<SuccessPayload> {

        static final List<String> PUBLISHED_EVENT_TYPES = new CopyOnWriteArrayList<>();

        @Override
        public void handle(final InboxEvent<SuccessPayload> event) {
            PUBLISHED_EVENT_TYPES.add(event.getEventType());
        }
    }

    static class FailingPublisher implements ForgeInboxEventHandler<FailingPayload> {

        @Override
        public void handle(final InboxEvent<FailingPayload> event) {
            throw new IllegalStateException("Forced publish failure");
        }
    }

    private record SuccessPayload(String value) implements ForgeInboxPayload {
    }

    private record FailingPayload(String value) implements ForgeInboxPayload {
    }

    private record UnsupportedPayload(String value) implements ForgeInboxPayload {
    }

    private record AggregatePayload(String value) implements ForgeInboxPayload {

        @Override
        public String aggregateTypeValue() {
            return "SITE";
        }

        @Override
        public Long aggregateId() {
            return 501L;
        }
    }

    private enum TestEventType implements ForgeInboxEventType {
        EMAIL_VERIFY(1L, "EMAIL_VERIFY", SuccessPayload.class),
        EMAIL_FAIL(2L, "EMAIL_FAIL", FailingPayload.class);

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
}
