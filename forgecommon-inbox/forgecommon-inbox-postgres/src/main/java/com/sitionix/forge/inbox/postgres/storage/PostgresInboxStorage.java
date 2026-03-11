package com.sitionix.forge.inbox.postgres.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PostgresInboxStorage implements InboxStorage {

    private static final String TABLE_NAME = "forge_inbox_events";
    private static final String AGGREGATE_TYPE_TABLE = "forge_inbox_aggregate_types";
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public PostgresInboxStorage(final NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper().findAndRegisterModules(), null);
    }

    public PostgresInboxStorage(final NamedParameterJdbcTemplate jdbcTemplate,
                                final ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, null);
    }

    public PostgresInboxStorage(final NamedParameterJdbcTemplate jdbcTemplate,
                                final EntityManager entityManager) {
        this(jdbcTemplate, new ObjectMapper().findAndRegisterModules(), entityManager);
    }

    public PostgresInboxStorage(final NamedParameterJdbcTemplate jdbcTemplate,
                                final ObjectMapper objectMapper,
                                final EntityManager entityManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void enqueue(final InboxRecord record) {
        final InboxRecord inboxRecord = Objects.requireNonNull(record, "record is required");
        final String idempotencyKey = this.requireNonBlank(inboxRecord.getIdempotencyKey(), "idempotencyKey");
        final Long aggregateTypeId = this.resolveAggregateTypeId(inboxRecord.getAggregateType());
        if (this.entityManager != null) {
            this.enqueueWithJpa(inboxRecord, idempotencyKey, aggregateTypeId);
            return;
        }
        this.enqueueWithJdbc(inboxRecord, idempotencyKey, aggregateTypeId);
    }

    private void enqueueWithJdbc(final InboxRecord inboxRecord,
                                 final String idempotencyKey,
                                 final Long aggregateTypeId) {
        final String sql = """
                INSERT INTO %s (
                    event_type,
                    payload,
                    headers,
                    metadata,
                    trace_id,
                    idempotency_key,
                    aggregate_type_id,
                    aggregate_id,
                    initiator_type,
                    initiator_id,
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
                    CAST(:headers AS JSONB),
                    CAST(:metadata AS JSONB),
                    :traceId,
                    :idempotencyKey,
                    :aggregateTypeId,
                    :aggregateId,
                    :initiatorType,
                    :initiatorId,
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
                .addValue("eventType", inboxRecord.getEventType())
                .addValue("payload", inboxRecord.getPayload())
                .addValue("headers", this.writeStringMap(inboxRecord.getHeaders()))
                .addValue("metadata", this.writeStringMap(inboxRecord.getMetadata()))
                .addValue("traceId", inboxRecord.getTraceId())
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("aggregateTypeId", aggregateTypeId)
                .addValue("aggregateId", inboxRecord.getAggregateId())
                .addValue("initiatorType", inboxRecord.getInitiatorType())
                .addValue("initiatorId", inboxRecord.getInitiatorId())
                .addValue("statusId", pendingStatus.getId())
                .addValue("retryCount", inboxRecord.getAttempts())
                .addValue("nextRetryAt", Timestamp.from(inboxRecord.getNextAttemptAt()))
                .addValue("lastError", inboxRecord.getLastError())
                .addValue("lockUntil", null)
                .addValue("createdAt", Timestamp.from(inboxRecord.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(inboxRecord.getUpdatedAt()));

        this.jdbcTemplate.update(sql, parameters);
    }

    private void enqueueWithJpa(final InboxRecord inboxRecord,
                                final String idempotencyKey,
                                final Long aggregateTypeId) {
        final String sql = """
                INSERT INTO %s (
                    event_type,
                    payload,
                    headers,
                    metadata,
                    trace_id,
                    idempotency_key,
                    aggregate_type_id,
                    aggregate_id,
                    initiator_type,
                    initiator_id,
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
                    CAST(:headers AS JSONB),
                    CAST(:metadata AS JSONB),
                    :traceId,
                    :idempotencyKey,
                    :aggregateTypeId,
                    :aggregateId,
                    :initiatorType,
                    :initiatorId,
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

        final Query query = this.entityManager.createNativeQuery(sql);
        query.setParameter("eventType", inboxRecord.getEventType());
        query.setParameter("payload", inboxRecord.getPayload());
        query.setParameter("headers", this.writeStringMap(inboxRecord.getHeaders()));
        query.setParameter("metadata", this.writeStringMap(inboxRecord.getMetadata()));
        query.setParameter("traceId", inboxRecord.getTraceId());
        query.setParameter("idempotencyKey", idempotencyKey);
        query.setParameter("aggregateTypeId", aggregateTypeId);
        query.setParameter("aggregateId", inboxRecord.getAggregateId());
        query.setParameter("initiatorType", inboxRecord.getInitiatorType());
        query.setParameter("initiatorId", inboxRecord.getInitiatorId());
        query.setParameter("statusId", InboxStatus.PENDING.getId());
        query.setParameter("retryCount", inboxRecord.getAttempts());
        query.setParameter("nextRetryAt", Timestamp.from(inboxRecord.getNextAttemptAt()));
        query.setParameter("lastError", inboxRecord.getLastError());
        query.setParameter("lockUntil", null);
        query.setParameter("createdAt", Timestamp.from(inboxRecord.getCreatedAt()));
        query.setParameter("updatedAt", Timestamp.from(inboxRecord.getUpdatedAt()));
        query.executeUpdate();
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
                       event.headers,
                       event.metadata,
                       event.trace_id,
                       event.idempotency_key,
                       aggregate_type.description AS aggregate_type,
                       event.aggregate_id,
                       event.initiator_type,
                       event.initiator_id,
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
                            .headers(this.readStringMap(resultSet.getString("headers")))
                            .metadata(this.readStringMap(resultSet.getString("metadata")))
                            .traceId(resultSet.getString("trace_id"))
                            .idempotencyKey(resultSet.getString("idempotency_key"))
                            .aggregateType(resultSet.getString("aggregate_type"))
                            .aggregateId(resultSet.getObject("aggregate_id", Long.class))
                            .initiatorType(resultSet.getString("initiator_type"))
                            .initiatorId(resultSet.getString("initiator_id"))
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

    private String writeStringMap(final Map<String, String> source) {
        final Map<String, String> effectiveSource = this.defaultMap(source);
        try {
            return this.objectMapper.writeValueAsString(effectiveSource);
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize inbox map field", exception);
        }
    }

    private Map<String, String> readStringMap(final String source) {
        if (source == null || source.isBlank()) {
            return Map.of();
        }
        try {
            final Map<String, Object> raw = this.objectMapper.readValue(source, MAP_TYPE_REFERENCE);
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            final Map<String, String> result = raw.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> String.valueOf(entry.getValue()),
                            (left, right) -> left,
                            LinkedHashMap::new));
            return result.isEmpty() ? Map.of() : Map.copyOf(result);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize inbox map field", exception);
        }
    }

    private Map<String, String> defaultMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }

    private String requireNonBlank(final String value,
                                   final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Inbox " + fieldName + " is required");
        }
        return value.trim();
    }
}
