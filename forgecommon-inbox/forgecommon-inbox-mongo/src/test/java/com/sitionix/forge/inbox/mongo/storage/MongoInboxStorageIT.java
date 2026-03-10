package com.sitionix.forge.inbox.mongo.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MongoInboxStorageIT {

    @Container
    private static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:7.0");

    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static MongoInboxStorage mongoInboxStorage;

    @BeforeAll
    static void setUp() {
        mongoClient = MongoClients.create(MONGO_DB_CONTAINER.getConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "forge_inbox");
        mongoInboxStorage = new MongoInboxStorage(mongoTemplate);
        new MongoInboxIndexesInitializer(mongoTemplate).afterPropertiesSet();
    }

    @AfterAll
    static void tearDown() {
        mongoClient.close();
        MONGO_DB_CONTAINER.stop();
    }

    @BeforeEach
    void cleanData() {
        mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME).deleteMany(new org.bson.Document());
    }

    @Test
    void givenPendingEvent_whenClaimAndMarkSent_thenPersistSentState() {
        //given
        final Instant now = Instant.parse("2026-01-01T10:00:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .headers(Map.of("h", "1"))
                .metadata(Map.of("m", "1"))
                .traceId("trace-1")
                .aggregateType("USER")
                .aggregateId(100L)
                .initiatorType("SYSTEM")
                .initiatorId("100")
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        mongoInboxStorage.enqueue(inboxRecord);

        //when
        final List<InboxRecord> claimed = mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING, InboxStatus.FAILED),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T10:01:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();
        mongoInboxStorage.markProcessed(
                id,
                Instant.parse("2026-01-01T10:01:00Z"),
                claimed.getFirst().getUpdatedAt());

        //then
        final String status = mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME)
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(id)))
                .first()
                .getString("status");

        assertThat(claimed).hasSize(1);
        assertThat(status).isEqualTo("PROCESSED");
    }

    @Test
    void givenPublishFailure_whenMarkFailed_thenMoveToDeadOnMaxRetries() {
        //given
        final Instant now = Instant.parse("2026-01-01T11:00:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .headers(Map.of())
                .metadata(Map.of())
                .aggregateType("USER")
                .aggregateId(101L)
                .initiatorType("SYSTEM")
                .initiatorId("101")
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        mongoInboxStorage.enqueue(inboxRecord);
        final List<InboxRecord> claimed = mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:01:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();

        //when
        mongoInboxStorage.markFailed(id,
                "boom",
                Duration.ofSeconds(10),
                1,
                Instant.parse("2026-01-01T11:01:00Z"),
                claimed.getFirst().getUpdatedAt());

        //then
        final org.bson.Document stored = mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME)
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(id)))
                .first();
        assertThat(stored.getString("status")).isEqualTo("DEAD");
        assertThat(stored.getInteger("attempts")).isEqualTo(1);
    }

    @Test
    void givenLockEnabledAndLeaseExpired_whenClaimPendingEvents_thenReclaimInProgressEvent() {
        //given
        final Instant now = Instant.parse("2026-01-01T11:20:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mongoInboxStorage.enqueue(inboxRecord);
        final List<InboxRecord> firstClaim = mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:21:00Z"),
                true,
                Duration.ofSeconds(1));

        //when
        final List<InboxRecord> secondClaimBeforeLease = mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:21:00.500Z"),
                true,
                Duration.ofSeconds(1));
        final List<InboxRecord> secondClaimAfterLease = mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:21:02Z"),
                true,
                Duration.ofSeconds(1));

        //then
        assertThat(firstClaim).hasSize(1);
        assertThat(secondClaimBeforeLease).isEmpty();
        assertThat(secondClaimAfterLease).hasSize(1);
        assertThat(secondClaimAfterLease.getFirst().getId()).isEqualTo(firstClaim.getFirst().getId());
    }

    @Test
    void givenConcurrentClaimRequests_whenClaimPendingEvents_thenOnlyOneWorkerClaimsRecord() throws ExecutionException, InterruptedException {
        //given
        final Instant now = Instant.parse("2026-01-01T11:25:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mongoInboxStorage.enqueue(inboxRecord);
        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        //when
        final Future<List<InboxRecord>> firstFuture = executorService.submit(() -> mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                1,
                Instant.parse("2026-01-01T11:26:00Z"),
                true,
                Duration.ofSeconds(30)));
        final Future<List<InboxRecord>> secondFuture = executorService.submit(() -> mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                1,
                Instant.parse("2026-01-01T11:26:00Z"),
                true,
                Duration.ofSeconds(30)));
        final List<InboxRecord> firstClaim;
        final List<InboxRecord> secondClaim;
        try {
            firstClaim = firstFuture.get();
            secondClaim = secondFuture.get();
        } finally {
            executorService.shutdown();
        }

        //then
        final int claimedCount = firstClaim.size() + secondClaim.size();
        assertThat(claimedCount).isEqualTo(1);
    }

    @Test
    void givenClaimedEvent_whenMarkFailedWithStaleUpdatedAt_thenIgnoreStaleUpdate() {
        //given
        final Instant now = Instant.parse("2026-01-01T11:30:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .headers(Map.of())
                .metadata(Map.of())
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        mongoInboxStorage.enqueue(inboxRecord);
        final List<InboxRecord> claimed = mongoInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:31:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();

        //when
        mongoInboxStorage.markFailed(
                id,
                "boom",
                Duration.ofSeconds(10),
                5,
                Instant.parse("2026-01-01T11:31:00Z"),
                claimed.getFirst().getUpdatedAt().minusSeconds(1));

        //then
        final org.bson.Document stored = mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME)
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(id)))
                .first();
        assertThat(stored.getString("status")).isEqualTo("IN_PROGRESS");
        assertThat(stored.getInteger("attempts")).isEqualTo(0);
    }

    @Test
    void givenSentEvents_whenDeleteSentBefore_thenDeleteOnlyExpired() {
        //given
        final Instant now = Instant.parse("2026-01-01T12:00:00Z");
        mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME).insertOne(new org.bson.Document()
                .append("eventType", "EMAIL_VERIFY")
                .append("payload", "{}")
                .append("headers", Map.of())
                .append("metadata", Map.of())
                .append("status", InboxStatus.PROCESSED.name())
                .append("attempts", 0)
                .append("nextAttemptAt", java.util.Date.from(now))
                .append("createdAt", java.util.Date.from(now.minus(Duration.ofDays(20))))
                .append("updatedAt", java.util.Date.from(now.minus(Duration.ofDays(20)))));
        mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME).insertOne(new org.bson.Document()
                .append("eventType", "EMAIL_VERIFY")
                .append("payload", "{}")
                .append("headers", Map.of())
                .append("metadata", Map.of())
                .append("status", InboxStatus.PROCESSED.name())
                .append("attempts", 0)
                .append("nextAttemptAt", java.util.Date.from(now))
                .append("createdAt", java.util.Date.from(now.minus(Duration.ofDays(1))))
                .append("updatedAt", java.util.Date.from(now.minus(Duration.ofDays(1)))));

        //when
        final int deleted = mongoInboxStorage.deleteProcessedBefore(now.minus(Duration.ofDays(14)));

        //then
        final long remaining = mongoTemplate.getCollection(MongoInboxStorage.COLLECTION_NAME)
                .countDocuments(new org.bson.Document("status", InboxStatus.PROCESSED.name()));
        assertThat(deleted).isEqualTo(1);
        assertThat(remaining).isEqualTo(1);
    }
}
