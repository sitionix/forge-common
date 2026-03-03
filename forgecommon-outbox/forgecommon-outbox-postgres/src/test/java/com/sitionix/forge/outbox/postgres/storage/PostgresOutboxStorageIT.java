package com.sitionix.forge.outbox.postgres.storage;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

@Testcontainers
class PostgresOutboxStorageIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("forge_outbox")
            .withUsername("forge")
            .withPassword("forge");

    private static DriverManagerDataSource dataSource;
    private static PostgresOutboxStorage postgresOutboxStorage;
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
        postgresOutboxStorage = new PostgresOutboxStorage(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDown() {
        POSTGRESQL_CONTAINER.stop();
        TimeZone.setDefault(previousDefaultTimeZone);
    }

    @Test
    void givenPendingEvent_whenClaimAndMarkSent_thenPersistSentState() {
        //given
        final Instant now = Instant.parse("2026-01-01T10:00:00Z");
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload("{\"value\":1}")
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

        postgresOutboxStorage.enqueue(outboxRecord);

        //when
        final List<OutboxRecord> claimed = postgresOutboxStorage.claimPendingEvents(
                EnumSet.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T10:01:00Z"),
                true,
                Duration.ofSeconds(30));
        postgresOutboxStorage.markSent(claimed.getFirst().getId());

        //then
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().getEventType()).isEqualTo("EMAIL_VERIFY");

        final Long statusId = jdbcTemplate.queryForObject(
                "SELECT status_id FROM forge_outbox_events WHERE id = ?",
                Long.class,
                Long.valueOf(claimed.getFirst().getId()));
        assertThat(statusId).isEqualTo(OutboxStatus.SENT.getId());
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

        postgresOutboxStorage.enqueue(outboxRecord);
        final List<OutboxRecord> claimed = postgresOutboxStorage.claimPendingEvents(
                EnumSet.of(OutboxStatus.PENDING),
                Set.of("EMAIL_VERIFY"),
                10,
                Instant.parse("2026-01-01T11:01:00Z"),
                true,
                Duration.ofSeconds(30));
        final String id = claimed.getFirst().getId();

        //when
        postgresOutboxStorage.markFailed(id,
                "boom",
                Duration.ofSeconds(10),
                1,
                Instant.parse("2026-01-01T11:01:00Z"));

        //then
        final Long statusId = jdbcTemplate.queryForObject(
                "SELECT status_id FROM forge_outbox_events WHERE id = ?",
                Long.class,
                Long.valueOf(id));
        final Integer retryCount = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM forge_outbox_events WHERE id = ?",
                Integer.class,
                Long.valueOf(id));

        assertThat(statusId).isEqualTo(OutboxStatus.DEAD.getId());
        assertThat(retryCount).isEqualTo(1);
    }

    @Test
    void givenSentEventOlderThanCutoff_whenDeleteSentBefore_thenRemoveOnlyExpired() {
        //given
        final Instant now = Instant.parse("2026-01-01T12:00:00Z");
        jdbcTemplate.update("""
                        INSERT INTO forge_outbox_events (
                            event_type, payload, status_id, retry_count, next_retry_at, created_at, updated_at
                        ) VALUES (
                            'EMAIL_VERIFY', '{}', 3, 0, ?, ?, ?
                        )
                        """,
                Timestamp.from(now),
                Timestamp.from(now.minus(Duration.ofDays(20))),
                Timestamp.from(now.minus(Duration.ofDays(20))));
        jdbcTemplate.update("""
                        INSERT INTO forge_outbox_events (
                            event_type, payload, status_id, retry_count, next_retry_at, created_at, updated_at
                        ) VALUES (
                            'EMAIL_VERIFY', '{}', 3, 0, ?, ?, ?
                        )
                        """,
                Timestamp.from(now),
                Timestamp.from(now.minus(Duration.ofDays(2))),
                Timestamp.from(now.minus(Duration.ofDays(2))));

        //when
        final int deleted = postgresOutboxStorage.deleteSentBefore(now.minus(Duration.ofDays(14)));

        //then
        final Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forge_outbox_events WHERE status_id = 3",
                Integer.class);
        assertThat(deleted).isEqualTo(1);
        assertThat(remaining).isEqualTo(1);
    }
}
