package com.sitionix.forge.outbox.mongo.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MongoOutboxStorageIT {

    @Container
    private static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:7.0");

    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static MongoOutboxStorage mongoOutboxStorage;

    @BeforeAll
    static void setUp() {
        mongoClient = MongoClients.create(MONGO_DB_CONTAINER.getConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "forge_outbox");
        mongoOutboxStorage = new MongoOutboxStorage(mongoTemplate);
        new MongoOutboxIndexesInitializer(mongoTemplate).afterPropertiesSet();
    }

    @AfterAll
    static void tearDown() {
        mongoClient.close();
        MONGO_DB_CONTAINER.stop();
    }

    @Test
    void givenPendingEvent_whenClaimAndMarkSent_thenPersistSentState() {
        //given
        final Instant now = Instant.parse("2026-01-01T10:00:00Z");
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .headers(Map.of("h", "1"))
                .metadata(Map.of("m", "1"))
                .traceId("trace-1")
                .aggregateType("USER")
                .aggregateId(100L)
                .initiatorType("SYSTEM")
                .initiatorId("100")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        mongoOutboxStorage.enqueue(outboxRecord);

        //when
        final List<OutboxRecord> claimed = mongoOutboxStorage.claimPendingEvents(
                EnumSet.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T10:01:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();
        mongoOutboxStorage.markSent(id);

        //then
        final String status = mongoTemplate.getCollection(MongoOutboxStorage.COLLECTION_NAME)
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(id)))
                .first()
                .getString("status");

        assertThat(claimed).hasSize(1);
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void givenPublishFailure_whenMarkFailed_thenMoveToDeadOnMaxRetries() {
        //given
        final Instant now = Instant.parse("2026-01-01T11:00:00Z");
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .headers(Map.of())
                .metadata(Map.of())
                .aggregateType("USER")
                .aggregateId(101L)
                .initiatorType("SYSTEM")
                .initiatorId("101")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        mongoOutboxStorage.enqueue(outboxRecord);
        final List<OutboxRecord> claimed = mongoOutboxStorage.claimPendingEvents(
                EnumSet.of(OutboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:01:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();

        //when
        mongoOutboxStorage.markFailed(id,
                "boom",
                Duration.ofSeconds(10),
                1,
                Instant.parse("2026-01-01T11:01:00Z"));

        //then
        final org.bson.Document stored = mongoTemplate.getCollection(MongoOutboxStorage.COLLECTION_NAME)
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(id)))
                .first();
        assertThat(stored.getString("status")).isEqualTo("DEAD");
        assertThat(stored.getInteger("attempts")).isEqualTo(1);
    }

    @Test
    void givenSentEvents_whenDeleteSentBefore_thenDeleteOnlyExpired() {
        //given
        final Instant now = Instant.parse("2026-01-01T12:00:00Z");
        mongoTemplate.getCollection(MongoOutboxStorage.COLLECTION_NAME).insertOne(new org.bson.Document()
                .append("eventType", "EMAIL_VERIFY")
                .append("payload", "{}")
                .append("headers", Map.of())
                .append("metadata", Map.of())
                .append("status", OutboxStatus.SENT.name())
                .append("attempts", 0)
                .append("nextAttemptAt", java.util.Date.from(now))
                .append("createdAt", java.util.Date.from(now.minus(Duration.ofDays(20))))
                .append("updatedAt", java.util.Date.from(now.minus(Duration.ofDays(20)))));
        mongoTemplate.getCollection(MongoOutboxStorage.COLLECTION_NAME).insertOne(new org.bson.Document()
                .append("eventType", "EMAIL_VERIFY")
                .append("payload", "{}")
                .append("headers", Map.of())
                .append("metadata", Map.of())
                .append("status", OutboxStatus.SENT.name())
                .append("attempts", 0)
                .append("nextAttemptAt", java.util.Date.from(now))
                .append("createdAt", java.util.Date.from(now.minus(Duration.ofDays(1))))
                .append("updatedAt", java.util.Date.from(now.minus(Duration.ofDays(1)))));

        //when
        final int deleted = mongoOutboxStorage.deleteSentBefore(now.minus(Duration.ofDays(14)));

        //then
        final long remaining = mongoTemplate.getCollection(MongoOutboxStorage.COLLECTION_NAME)
                .countDocuments(new org.bson.Document("status", OutboxStatus.SENT.name()));
        assertThat(deleted).isEqualTo(1);
        assertThat(remaining).isEqualTo(1);
    }
}
