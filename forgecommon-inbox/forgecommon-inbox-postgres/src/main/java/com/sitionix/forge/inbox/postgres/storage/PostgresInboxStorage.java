package com.sitionix.forge.inbox.postgres.storage;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PostgresInboxStorage implements InboxStorage {

    private static final String TABLE_NAME = "forge_inbox_events";
    private static final String AGGREGATE_TYPE_TABLE = "forge_inbox_aggregate_types";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresInboxStorage(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void enqueue(final InboxRecord record) {
        final Long aggregateTypeId = this.resolveAggregateTypeId(record.getAggregateType());

        final String sql = """
                INSERT INTO %s (
                    event_type,
                    payload,
                    trace_id,
                    idempotency_key,
                    aggregate_type_id,
                    aggregate_id,
                    status_id,
                    retry_count,
                    next_retry_at,
                    last_error,
                    lock_until,
                    created_at,
                    updated_at
                ) VALUES (
                    :eventType,
                    :payload,
                    :traceId,
                    :idempotencyKey,
                    :aggregateTypeId,
                    :aggregateId,
                    :statusId,
                    :retryCount,
                    :nextRetryAt,
                    :lastError,
                    :lockUntil,
                    :createdAt,
                    :updatedAt
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """.formatted(TABLE_NAME);

        final InboxStatus pendingStatus = InboxStatus.PENDING;
        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventType", record.getEventType())
                .addValue("payload", record.getPayload())
                .addValue("traceId", record.getTraceId())
                .addValue("idempotencyKey", record.getIdempotencyKey())
                .addValue("aggregateTypeId", aggregateTypeId)
                .addValue("aggregateId", record.getAggregateId())
                .addValue("statusId", pendingStatus.getId())
                .addValue("retryCount", record.getAttempts())
                .addValue("nextRetryAt", Timestamp.from(record.getNextAttemptAt()))
                .addValue("lastError", record.getLastError())
                .addValue("lockUntil", null)
                .addValue("createdAt", Timestamp.from(record.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(record.getUpdatedAt()));

        this.jdbcTemplate.update(sql, parameters);
    }

    @Override
    @Transactional
    public List<InboxRecord> claimPendingEvents(final Set<InboxStatus> eventStatuses,
                                                 final Set<String> eventTypes,
                                                 final int batchSize,
                                                 final Instant now,
                                                 final boolean lockEnabled,
                                                 final Duration lockLease) {
        final boolean filterByEventTypes = eventTypes != null && !eventTypes.isEmpty() && !eventTypes.contains("*");
        final List<Long> statusIds = eventStatuses.stream().map(InboxStatus::getId).toList();
        final Timestamp nowTimestamp = Timestamp.from(now);
        final Timestamp lockUntil = lockEnabled ? Timestamp.from(now.plus(lockLease)) : null;
        final String selectSql = """
                SELECT id
                FROM %s
                WHERE (
                        status_id IN (:statusIds)
                     OR (
                            :lockEnabled = TRUE
                        AND status_id = :inProgressStatusId
                        AND lock_until IS NOT NULL
                        AND lock_until <= :now
                     )
                )
                  AND (:filterByEventTypes = FALSE OR event_type IN (:eventTypes))
                  AND next_retry_at <= :now
                ORDER BY next_retry_at ASC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
                """.formatted(TABLE_NAME);

        final MapSqlParameterSource selectParams = new MapSqlParameterSource()
                .addValue("statusIds", statusIds)
                .addValue("eventTypes", filterByEventTypes ? eventTypes : List.of("__all__"))
                .addValue("filterByEventTypes", filterByEventTypes)
                .addValue("inProgressStatusId", InboxStatus.IN_PROGRESS.getId())
                .addValue("lockEnabled", lockEnabled)
                .addValue("now", nowTimestamp)
                .addValue("batchSize", batchSize);

        final List<Long> ids = this.jdbcTemplate.query(selectSql, selectParams, (resultSet, rowNum) -> resultSet.getLong("id"));
        if (ids.isEmpty()) {
            return List.of();
        }

        final String updateSql = """
                UPDATE %s
                SET status_id = :statusId,
                    lock_until = :lockUntil,
                    updated_at = :updatedAt
                WHERE id IN (:ids)
                """.formatted(TABLE_NAME);

        this.jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                .addValue("statusId", InboxStatus.IN_PROGRESS.getId())
                .addValue("lockUntil", lockUntil)
                .addValue("updatedAt", nowTimestamp)
                .addValue("ids", ids));

        final String selectClaimedSql = """
                SELECT event.id,
                       event.event_type,
                       event.payload,
                       event.trace_id,
                       event.idempotency_key,
                       aggregate_type.description AS aggregate_type,
                       event.aggregate_id,
                       event.status_id,
                       event.retry_count,
                       event.next_retry_at,
                       event.last_error,
                       event.created_at,
                       event.updated_at
                FROM %s event
                LEFT JOIN %s aggregate_type ON aggregate_type.id = event.aggregate_type_id
                WHERE event.id IN (:ids)
                ORDER BY event.next_retry_at ASC, event.created_at ASC
                """.formatted(TABLE_NAME, AGGREGATE_TYPE_TABLE);

        return this.jdbcTemplate.query(selectClaimedSql,
                new MapSqlParameterSource().addValue("ids", ids),
                (resultSet, rowNum) -> {
                    final Timestamp nextRetryAt = resultSet.getTimestamp("next_retry_at");
                    final Timestamp createdAt = resultSet.getTimestamp("created_at");
                    final Timestamp updatedAt = resultSet.getTimestamp("updated_at");

                    return InboxRecord.builder()
                            .id(String.valueOf(resultSet.getLong("id")))
                            .eventType(resultSet.getString("event_type"))
                            .payload(resultSet.getString("payload"))
                            .headers(Map.of())
                            .metadata(Map.of())
                            .traceId(resultSet.getString("trace_id"))
                            .idempotencyKey(resultSet.getString("idempotency_key"))
                            .aggregateType(resultSet.getString("aggregate_type"))
                            .aggregateId(resultSet.getObject("aggregate_id", Long.class))
                            .initiatorType(null)
                            .initiatorId(null)
                            .status(InboxStatus.fromId(resultSet.getLong("status_id")))
                            .attempts(resultSet.getInt("retry_count"))
                            .nextAttemptAt(nextRetryAt == null ? null : nextRetryAt.toInstant())
                            .lastError(resultSet.getString("last_error"))
                            .createdAt(createdAt == null ? null : createdAt.toInstant())
                            .updatedAt(updatedAt == null ? null : updatedAt.toInstant())
                            .build();
                });
    }

    @Override
    @Transactional
    public void markProcessed(final String inboxEventId,
                         final Instant now,
                         final Instant expectedUpdatedAt) {
        final String sql = """
                UPDATE %s
                SET status_id = :statusId,
                    last_error = NULL,
                    lock_until = NULL,
                    updated_at = :updatedAt
                WHERE id = :id
                  AND status_id = :inProgressStatusId
                  AND updated_at = :expectedUpdatedAt
                """.formatted(TABLE_NAME);

        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("statusId", InboxStatus.PROCESSED.getId())
                .addValue("updatedAt", Timestamp.from(now))
                .addValue("id", Long.valueOf(inboxEventId))
                .addValue("inProgressStatusId", InboxStatus.IN_PROGRESS.getId())
                .addValue("expectedUpdatedAt", Timestamp.from(expectedUpdatedAt)));
    }

    @Override
    @Transactional
    public void markFailed(final String inboxEventId,
                           final String errorMessage,
                           final Duration retryDelay,
                           final int maxRetries,
                           final Instant now,
                           final Instant expectedUpdatedAt) {
        final String sql = """
                UPDATE %s
                SET retry_count = retry_count + 1,
                    last_error = :lastError,
                    next_retry_at = :nextRetryAt,
                    lock_until = NULL,
                    status_id = CASE
                        WHEN retry_count + 1 >= :maxRetries THEN :deadStatusId
                        ELSE :failedStatusId
                    END,
                    updated_at = :updatedAt
                WHERE id = :id
                  AND status_id = :inProgressStatusId
                  AND updated_at = :expectedUpdatedAt
                """.formatted(TABLE_NAME);

        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("lastError", errorMessage)
                .addValue("nextRetryAt", Timestamp.from(now.plus(retryDelay)))
                .addValue("maxRetries", maxRetries)
                .addValue("deadStatusId", InboxStatus.DEAD.getId())
                .addValue("failedStatusId", InboxStatus.FAILED.getId())
                .addValue("updatedAt", Timestamp.from(now))
                .addValue("id", Long.valueOf(inboxEventId))
                .addValue("inProgressStatusId", InboxStatus.IN_PROGRESS.getId())
                .addValue("expectedUpdatedAt", Timestamp.from(expectedUpdatedAt)));
    }

    @Override
    @Transactional
    public int deleteProcessedBefore(final Instant cutoff) {
        final String sql = """
                DELETE FROM %s
                WHERE status_id = :processedStatusId
                  AND created_at < :cutoff
                """.formatted(TABLE_NAME);

        return this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("processedStatusId", InboxStatus.PROCESSED.getId())
                .addValue("cutoff", Timestamp.from(cutoff)));
    }

    private Long resolveAggregateTypeId(final String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return null;
        }
        final String normalizedAggregateType = aggregateType.trim();
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("description", normalizedAggregateType);
        final String selectSql = """
                SELECT id
                FROM %s
                WHERE description = :description
                """.formatted(AGGREGATE_TYPE_TABLE);
        final List<Long> ids = this.jdbcTemplate.query(selectSql, params, (resultSet, rowNum) -> resultSet.getLong("id"));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown inbox aggregateType '%s'. Seed forge_inbox_aggregate_types via migration."
                            .formatted(normalizedAggregateType));
        }
        return ids.getFirst();
    }
}
