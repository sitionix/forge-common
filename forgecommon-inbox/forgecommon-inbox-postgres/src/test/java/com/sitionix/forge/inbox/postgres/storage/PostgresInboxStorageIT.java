package com.sitionix.forge.inbox.postgres.storage;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresInboxStorageIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("forge_inbox")
            .withUsername("forge")
            .withPassword("forge");

    private static DriverManagerDataSource dataSource;
    private static PostgresInboxStorage postgresInboxStorage;
    private static JdbcTemplate jdbcTemplate;
    private static TimeZone previousDefaultTimeZone;

    @BeforeAll
    static void setUp() {
        previousDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        dataSource = new DriverManagerDataSource(
                POSTGRESQL_CONTAINER.getJdbcUrl(),
                POSTGRESQL_CONTAINER.getUsername(),
                POSTGRESQL_CONTAINER.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        postgresInboxStorage = new PostgresInboxStorage(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDown() {
        POSTGRESQL_CONTAINER.stop();
        TimeZone.setDefault(previousDefaultTimeZone);
    }

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM forge_inbox_events");
        jdbcTemplate.update("DELETE FROM forge_inbox_aggregate_types");
        jdbcTemplate.update("""
                INSERT INTO forge_inbox_aggregate_types (id, description)
                VALUES (1, 'USER')
                ON CONFLICT (id) DO UPDATE SET description = EXCLUDED.description
                """);
    }

    @Test
    void givenPendingEvent_whenClaimAndMarkSent_thenPersistSentState() {
        //given
        final Instant now = Instant.parse("2026-01-01T10:00:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":1}")
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

        postgresInboxStorage.enqueue(inboxRecord);

        //when
        final List<InboxRecord> claimed = postgresInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING, InboxStatus.FAILED),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T10:01:00Z"),
                true,
                Duration.ofSeconds(30));
        postgresInboxStorage.markProcessed(
                claimed.getFirst().getId(),
                Instant.parse("2026-01-01T10:01:00Z"),
                claimed.getFirst().getUpdatedAt());

        //then
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().getEventType()).isEqualTo("EMAIL_VERIFY");

        final Long statusId = jdbcTemplate.queryForObject(
                "SELECT status_id FROM forge_inbox_events WHERE id = ?",
                Long.class,
                Long.valueOf(claimed.getFirst().getId()));
        assertThat(statusId).isEqualTo(InboxStatus.PROCESSED.getId());
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

        postgresInboxStorage.enqueue(inboxRecord);
        final List<InboxRecord> claimed = postgresInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:01:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();

        //when
        postgresInboxStorage.markFailed(id,
                "boom",
                Duration.ofSeconds(10),
                1,
                Instant.parse("2026-01-01T11:01:00Z"),
                claimed.getFirst().getUpdatedAt());

        //then
        final Long statusId = jdbcTemplate.queryForObject(
                "SELECT status_id FROM forge_inbox_events WHERE id = ?",
                Long.class,
                Long.valueOf(id));
        final Integer retryCount = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM forge_inbox_events WHERE id = ?",
                Integer.class,
                Long.valueOf(id));

        assertThat(statusId).isEqualTo(InboxStatus.DEAD.getId());
        assertThat(retryCount).isEqualTo(1);
    }

    @Test
    void givenUnknownAggregateType_whenEnqueue_thenThrowIllegalArgumentException() {
        //given
        final Instant now = Instant.parse("2026-01-01T11:10:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .aggregateType("SITE")
                .aggregateId(500L)
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        //then
        assertThatThrownBy(() -> postgresInboxStorage.enqueue(inboxRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown inbox aggregateType 'SITE'");
    }

    @Test
    void givenClaimedEvent_whenMarkSentWithStaleUpdatedAt_thenIgnoreStaleUpdate() {
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

        postgresInboxStorage.enqueue(inboxRecord);
        final List<InboxRecord> claimed = postgresInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:31:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();

        //when
        postgresInboxStorage.markProcessed(
                id,
                Instant.parse("2026-01-01T11:31:10Z"),
                claimed.getFirst().getUpdatedAt().minusSeconds(1));

        //then
        final Long statusId = jdbcTemplate.queryForObject(
                "SELECT status_id FROM forge_inbox_events WHERE id = ?",
                Long.class,
                Long.valueOf(id));
        assertThat(statusId).isEqualTo(InboxStatus.IN_PROGRESS.getId());
    }

    @Test
    void givenLockEnabledAndLeaseExpired_whenClaimPendingEvents_thenReclaimInProgressEvent() {
        //given
        final Instant now = Instant.parse("2026-01-01T11:40:00Z");
        final InboxRecord inboxRecord = InboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .status(InboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        postgresInboxStorage.enqueue(inboxRecord);
        final List<InboxRecord> firstClaim = postgresInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:41:00Z"),
                true,
                Duration.ofSeconds(1));

        //when
        final List<InboxRecord> secondClaimBeforeLease = postgresInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:41:00.500Z"),
                true,
                Duration.ofSeconds(1));
        final List<InboxRecord> secondClaimAfterLease = postgresInboxStorage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:41:02Z"),
                true,
                Duration.ofSeconds(1));

        //then
        assertThat(firstClaim).hasSize(1);
        assertThat(secondClaimBeforeLease).isEmpty();
        assertThat(secondClaimAfterLease).hasSize(1);
        assertThat(secondClaimAfterLease.getFirst().getId()).isEqualTo(firstClaim.getFirst().getId());
    }

    @Test
    void givenSentEventOlderThanCutoff_whenDeleteSentBefore_thenRemoveOnlyExpired() {
        //given
        final Instant now = Instant.parse("2026-01-01T12:00:00Z");
        jdbcTemplate.update("""
                        INSERT INTO forge_inbox_events (
                            event_type, payload, status_id, retry_count, next_retry_at, created_at, updated_at
                        ) VALUES (
                            'EMAIL_VERIFY', '{}', 3, 0, ?, ?, ?
                        )
                        """,
                Timestamp.from(now),
                Timestamp.from(now.minus(Duration.ofDays(20))),
                Timestamp.from(now.minus(Duration.ofDays(20))));
        jdbcTemplate.update("""
                        INSERT INTO forge_inbox_events (
                            event_type, payload, status_id, retry_count, next_retry_at, created_at, updated_at
                        ) VALUES (
                            'EMAIL_VERIFY', '{}', 3, 0, ?, ?, ?
                        )
                        """,
                Timestamp.from(now),
                Timestamp.from(now.minus(Duration.ofDays(2))),
                Timestamp.from(now.minus(Duration.ofDays(2))));

        //when
        final int deleted = postgresInboxStorage.deleteProcessedBefore(now.minus(Duration.ofDays(14)));

        //then
        final Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forge_inbox_events WHERE status_id = 3",
                Integer.class);
        assertThat(deleted).isEqualTo(1);
        assertThat(remaining).isEqualTo(1);
    }
}
