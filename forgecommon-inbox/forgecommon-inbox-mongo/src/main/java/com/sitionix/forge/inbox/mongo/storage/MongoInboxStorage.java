package com.sitionix.forge.inbox.mongo.storage;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MongoInboxStorage implements InboxStorage {

    public static final String COLLECTION_NAME = "forge_inbox_events";

    private final MongoTemplate mongoTemplate;

    public MongoInboxStorage(final MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(mongoTemplate, "mongoTemplate is required");
    }

    @Override
    public void enqueue(final InboxRecord record) {
        final InboxRecord inboxRecord = Objects.requireNonNull(record, "record is required");
        final String idempotencyKey = this.requireNonBlank(inboxRecord.getIdempotencyKey(), "idempotencyKey");

        final Document document = new Document();
        document.put("eventType", inboxRecord.getEventType());
        document.put("payload", inboxRecord.getPayload());
        document.put("headers", defaultMap(inboxRecord.getHeaders()));
        document.put("metadata", defaultMap(inboxRecord.getMetadata()));
        document.put("traceId", inboxRecord.getTraceId());
        document.put("idempotencyKey", idempotencyKey);
        document.put("aggregateType", inboxRecord.getAggregateType());
        document.put("aggregateId", inboxRecord.getAggregateId());
        document.put("initiatorType", inboxRecord.getInitiatorType());
        document.put("initiatorId", inboxRecord.getInitiatorId());
        document.put("status", InboxStatus.PENDING.name());
        document.put("attempts", inboxRecord.getAttempts());
        document.put("nextAttemptAt", Date.from(inboxRecord.getNextAttemptAt()));
        document.put("lastError", inboxRecord.getLastError());
        document.put("createdAt", Date.from(inboxRecord.getCreatedAt()));
        document.put("updatedAt", Date.from(inboxRecord.getUpdatedAt()));

        try {
            this.mongoTemplate.getCollection(COLLECTION_NAME).insertOne(document);
        } catch (final MongoWriteException exception) {
            if (!ErrorCategory.DUPLICATE_KEY.equals(exception.getError().getCategory())) {
                throw exception;
            }
        }
    }

    @Override
    public List<InboxRecord> claimPendingEvents(final Set<InboxStatus> eventStatuses,
                                                 final Set<String> eventTypes,
                                                 final int batchSize,
                                                 final Instant now,
                                                 final boolean lockEnabled,
                                                 final Duration lockLease) {
        Objects.requireNonNull(eventStatuses, "eventStatuses is required");
        Objects.requireNonNull(now, "now is required");
        if (eventStatuses.isEmpty() || batchSize < 1) {
            return List.of();
        }
        final Duration effectiveLockLease = lockEnabled
                ? Objects.requireNonNull(lockLease, "lockLease is required when lockEnabled")
                : lockLease;
        if (lockEnabled && (effectiveLockLease.isZero() || effectiveLockLease.isNegative())) {
            throw new IllegalArgumentException("lockLease must be greater than 0 when lockEnabled");
        }
        final boolean filterByEventTypes = eventTypes != null && !eventTypes.isEmpty() && !eventTypes.contains("*");
        final List<String> statuses = eventStatuses.stream().map(Enum::name).toList();
        return IntStream.range(0, batchSize)
                .mapToObj(index -> this.claimSinglePendingEvent(
                        filterByEventTypes,
                        eventTypes,
                        statuses,
                        now,
                        lockEnabled,
                        effectiveLockLease))
                .takeWhile(Objects::nonNull)
                .toList();
    }

    @Override
    public void markProcessed(final String inboxEventId,
                         final Instant now,
                         final Instant expectedUpdatedAt) {
        final Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(this.objectIdOrString(inboxEventId)),
                Criteria.where("status").is(InboxStatus.IN_PROGRESS.name()),
                Criteria.where("updatedAt").is(Date.from(expectedUpdatedAt))));
        final Update update = new Update()
                .set("status", InboxStatus.PROCESSED.name())
                .set("updatedAt", Date.from(now))
                .set("lastError", null)
                .unset("lockUntil");

        this.mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    @Override
    public void markFailed(final String inboxEventId,
                           final String errorMessage,
                           final Duration retryDelay,
                           final int maxRetries,
                           final Instant now,
                           final Instant expectedUpdatedAt) {
        final Object id = this.objectIdOrString(inboxEventId);
        final Document nextAttemptsExpression = new Document("$add",
                List.of(new Document("$ifNull", List.of("$attempts", 0)), 1));
        final Document nextStatusExpression = new Document("$cond",
                List.of(
                        new Document("$gte", List.of(nextAttemptsExpression, maxRetries)),
                        InboxStatus.DEAD.name(),
                        InboxStatus.FAILED.name()));

        final Document setDocument = new Document()
                .append("attempts", nextAttemptsExpression)
                .append("status", nextStatusExpression)
                .append("lastError", errorMessage)
                .append("nextAttemptAt", Date.from(now.plus(retryDelay)))
                .append("updatedAt", Date.from(now))
                .append("lockUntil", null);

        this.mongoTemplate.getCollection(COLLECTION_NAME)
                .findOneAndUpdate(
                        new Document("_id", id)
                                .append("status", InboxStatus.IN_PROGRESS.name())
                                .append("updatedAt", Date.from(expectedUpdatedAt)),
                        List.of(new Document("$set", setDocument)),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    @Override
    public int deleteProcessedBefore(final Instant cutoff) {
        final Query query = Query.query(new Criteria().andOperator(
                Criteria.where("status").is(InboxStatus.PROCESSED.name()),
                Criteria.where("createdAt").lt(Date.from(cutoff))));
        return Math.toIntExact(this.mongoTemplate.remove(query, COLLECTION_NAME).getDeletedCount());
    }

    private InboxRecord toInboxRecord(final Document source) {
        final Date nextAttemptAt = source.getDate("nextAttemptAt");
        final Date createdAt = source.getDate("createdAt");
        final Date updatedAt = source.getDate("updatedAt");

        return InboxRecord.builder()
                .id(this.asId(source.get("_id")))
                .eventType(source.getString("eventType"))
                .payload(source.getString("payload"))
                .headers(this.readStringMap(source, "headers"))
                .metadata(this.readStringMap(source, "metadata"))
                .traceId(source.getString("traceId"))
                .idempotencyKey(source.getString("idempotencyKey"))
                .aggregateType(source.getString("aggregateType"))
                .aggregateId(source.getLong("aggregateId"))
                .initiatorType(source.getString("initiatorType"))
                .initiatorId(source.getString("initiatorId"))
                .status(InboxStatus.valueOf(source.getString("status")))
                .attempts(source.getInteger("attempts", 0))
                .nextAttemptAt(nextAttemptAt == null ? null : nextAttemptAt.toInstant())
                .lastError(source.getString("lastError"))
                .createdAt(createdAt == null ? null : createdAt.toInstant())
                .updatedAt(updatedAt == null ? null : updatedAt.toInstant())
                .build();
    }

    private Object objectIdOrString(final String id) {
        if (ObjectId.isValid(id)) {
            return new ObjectId(id);
        }
        return id;
    }

    private String asId(final Object id) {
        if (id instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return String.valueOf(id);
    }

    private static Map<String, String> defaultMap(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }

    private Map<String, String> readStringMap(final Document source,
                                              final String field) {
        final Object raw = source.get(field);
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        final Map<String, String> result = rawMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private InboxRecord claimSinglePendingEvent(final boolean filterByEventTypes,
                                                final Set<String> eventTypes,
                                                final List<String> statuses,
                                                final Instant now,
                                                final boolean lockEnabled,
                                                final Duration lockLease) {
        final Query query = new Query();
        if (filterByEventTypes) {
            query.addCriteria(Criteria.where("eventType").in(eventTypes));
        }
        query.addCriteria(Criteria.where("nextAttemptAt").lte(Date.from(now)));
        if (lockEnabled) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("status").in(statuses),
                    new Criteria().andOperator(
                            Criteria.where("status").is(InboxStatus.IN_PROGRESS.name()),
                            new Criteria().orOperator(
                                    Criteria.where("lockUntil").exists(false),
                                    Criteria.where("lockUntil").lte(Date.from(now))))));
        } else {
            query.addCriteria(Criteria.where("status").in(statuses));
        }
        query.with(Sort.by(Sort.Order.asc("nextAttemptAt"), Sort.Order.asc("_id")));

        final Update update = new Update()
                .set("status", InboxStatus.IN_PROGRESS.name())
                .set("updatedAt", Date.from(now));
        if (lockEnabled) {
            update.set("lockUntil", Date.from(now.plus(lockLease)));
        } else {
            update.unset("lockUntil");
        }

        final FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        final Document claimed = this.mongoTemplate.findAndModify(query, update, options, Document.class, COLLECTION_NAME);
        if (claimed == null) {
            return null;
        }
        return this.toInboxRecord(claimed);
    }

    private String requireNonBlank(final String value,
                                   final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Inbox " + fieldName + " is required");
        }
        return value.trim();
    }
}
