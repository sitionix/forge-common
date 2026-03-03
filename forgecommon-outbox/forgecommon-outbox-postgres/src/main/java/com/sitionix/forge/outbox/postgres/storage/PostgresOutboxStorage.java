package com.sitionix.forge.outbox.postgres.storage;

import com.sitionix.forge.outbox.core.model.OutboxAggregateType;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PostgresOutboxStorage implements OutboxStorage {

    private static final String TABLE_NAME = "forge_outbox_events";
    private static final String AGGREGATE_TYPE_TABLE = "forge_outbox_aggregate_types";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresOutboxStorage(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void enqueue(final OutboxRecord record) {
        final Long aggregateTypeId = this.resolveAggregateTypeId(record.getAggregateType());

        final String sql = """
                INSERT INTO %s (
                    event_type,
                    payload,
                    trace_id,
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
                """.formatted(TABLE_NAME);

        final OutboxStatus pendingStatus = OutboxStatus.PENDING;
        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventType", record.getEventType())
                .addValue("payload", record.getPayload())
                .addValue("traceId", record.getTraceId())
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
    public List<OutboxRecord> claimPendingEvents(final Set<OutboxStatus> eventStatuses,
                                                 final Set<String> eventTypes,
                                                 final int batchSize,
                                                 final Instant now,
                                                 final boolean lockEnabled,
                                                 final Duration lockLease) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of();
        }

        final List<Long> statusIds = eventStatuses.stream().map(OutboxStatus::getId).toList();
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
                  AND event_type IN (:eventTypes)
                  AND next_retry_at <= :now
                ORDER BY next_retry_at ASC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
                """.formatted(TABLE_NAME);

        final MapSqlParameterSource selectParams = new MapSqlParameterSource()
                .addValue("statusIds", statusIds)
                .addValue("eventTypes", eventTypes)
                .addValue("inProgressStatusId", OutboxStatus.IN_PROGRESS.getId())
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
                .addValue("statusId", OutboxStatus.IN_PROGRESS.getId())
                .addValue("lockUntil", lockUntil)
                .addValue("updatedAt", nowTimestamp)
                .addValue("ids", ids));

        final String selectClaimedSql = """
                SELECT id,
                       event_type,
                       payload,
                       trace_id,
                       aggregate_type_id,
                       aggregate_id,
                       status_id,
                       retry_count,
                       next_retry_at,
                       last_error,
                       created_at,
                       updated_at
                FROM %s
                WHERE id IN (:ids)
                ORDER BY next_retry_at ASC, created_at ASC
                """.formatted(TABLE_NAME);

        return this.jdbcTemplate.query(selectClaimedSql,
                new MapSqlParameterSource().addValue("ids", ids),
                (resultSet, rowNum) -> {
                    final Timestamp nextRetryAt = resultSet.getTimestamp("next_retry_at");
                    final Timestamp createdAt = resultSet.getTimestamp("created_at");
                    final Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    final Long aggregateTypeId = resultSet.getObject("aggregate_type_id", Long.class);

                    return OutboxRecord.builder()
                            .id(String.valueOf(resultSet.getLong("id")))
                            .eventType(resultSet.getString("event_type"))
                            .payload(resultSet.getString("payload"))
                            .headers(Map.of())
                            .metadata(Map.of())
                            .traceId(resultSet.getString("trace_id"))
                            .aggregateType(aggregateTypeId == null
                                    ? null
                                    : OutboxAggregateType.fromId(aggregateTypeId).getDescription())
                            .aggregateId(resultSet.getObject("aggregate_id", Long.class))
                            .initiatorType(null)
                            .initiatorId(null)
                            .status(OutboxStatus.fromId(resultSet.getLong("status_id")))
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
    public void markSent(final String outboxEventId) {
        final String sql = """
                UPDATE %s
                SET status_id = :statusId,
                    last_error = NULL,
                    lock_until = NULL,
                    updated_at = :updatedAt
                WHERE id = :id
                """.formatted(TABLE_NAME);

        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("statusId", OutboxStatus.SENT.getId())
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("id", Long.valueOf(outboxEventId)));
    }

    @Override
    @Transactional
    public void markFailed(final String outboxEventId,
                           final String errorMessage,
                           final Duration retryDelay,
                           final int maxRetries,
                           final Instant now) {
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
                """.formatted(TABLE_NAME);

        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("lastError", errorMessage)
                .addValue("nextRetryAt", Timestamp.from(now.plus(retryDelay)))
                .addValue("maxRetries", maxRetries)
                .addValue("deadStatusId", OutboxStatus.DEAD.getId())
                .addValue("failedStatusId", OutboxStatus.FAILED.getId())
                .addValue("updatedAt", Timestamp.from(now))
                .addValue("id", Long.valueOf(outboxEventId)));
    }

    @Override
    @Transactional
    public int deleteSentBefore(final Instant cutoff) {
        final String sql = """
                DELETE FROM %s
                WHERE status_id = :sentStatusId
                  AND created_at < :cutoff
                """.formatted(TABLE_NAME);

        return this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sentStatusId", OutboxStatus.SENT.getId())
                .addValue("cutoff", Timestamp.from(cutoff)));
    }

    private Long resolveAggregateTypeId(final String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return null;
        }
        final OutboxAggregateType resolved = OutboxAggregateType.fromDescription(aggregateType);
        final String sql = """
                INSERT INTO %s (id, description)
                VALUES (:id, :description)
                ON CONFLICT (id) DO UPDATE SET description = EXCLUDED.description
                """.formatted(AGGREGATE_TYPE_TABLE);
        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", resolved.getId())
                .addValue("description", resolved.getDescription()));
        return resolved.getId();
    }
}
