package com.sitionix.forge.outbox.mongo.storage;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MongoOutboxStorage implements OutboxStorage {

    public static final String COLLECTION_NAME = "forge_outbox_events";

    private final MongoTemplate mongoTemplate;

    public MongoOutboxStorage(final MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void enqueue(final OutboxRecord record) {
        final Document document = new Document();
        document.put("eventType", record.getEventType());
        document.put("payload", record.getPayload());
        document.put("headers", defaultMap(record.getHeaders()));
        document.put("metadata", defaultMap(record.getMetadata()));
        document.put("traceId", record.getTraceId());
        document.put("aggregateType", record.getAggregateType());
        document.put("aggregateId", record.getAggregateId());
        document.put("initiatorType", record.getInitiatorType());
        document.put("initiatorId", record.getInitiatorId());
        document.put("status", OutboxStatus.PENDING.name());
        document.put("attempts", record.getAttempts());
        document.put("nextAttemptAt", Date.from(record.getNextAttemptAt()));
        document.put("lastError", record.getLastError());
        document.put("createdAt", Date.from(record.getCreatedAt()));
        document.put("updatedAt", Date.from(record.getUpdatedAt()));

        this.mongoTemplate.getCollection(COLLECTION_NAME).insertOne(document);
    }

    @Override
    public List<OutboxRecord> claimPendingEvents(final Set<OutboxStatus> eventStatuses,
                                                 final Set<String> eventTypes,
                                                 final int batchSize,
                                                 final Instant now,
                                                 final boolean lockEnabled,
                                                 final Duration lockLease) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of();
        }

        final List<String> statuses = eventStatuses.stream().map(Enum::name).toList();
        final List<OutboxRecord> claimedEvents = new ArrayList<>();

        for (int index = 0; index < batchSize; index++) {
            final Query query = new Query();
            query.addCriteria(Criteria.where("eventType").in(eventTypes));
            query.addCriteria(Criteria.where("nextAttemptAt").lte(Date.from(now)));
            if (lockEnabled) {
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("status").in(statuses),
                        new Criteria().andOperator(
                                Criteria.where("status").is(OutboxStatus.IN_PROGRESS.name()),
                                new Criteria().orOperator(
                                        Criteria.where("lockUntil").exists(false),
                                        Criteria.where("lockUntil").lte(Date.from(now))))));
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("lockUntil").exists(false),
                        Criteria.where("lockUntil").lte(Date.from(now))));
            } else {
                query.addCriteria(Criteria.where("status").in(statuses));
            }
            query.with(Sort.by(Sort.Order.asc("nextAttemptAt"), Sort.Order.asc("_id")));

            final Update update = new Update()
                    .set("status", OutboxStatus.IN_PROGRESS.name())
                    .set("updatedAt", Date.from(now));
            if (lockEnabled) {
                update.set("lockUntil", Date.from(now.plus(lockLease)));
            } else {
                update.unset("lockUntil");
            }

            final FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
            final Document claimed = this.mongoTemplate.findAndModify(query, update, options, Document.class, COLLECTION_NAME);
            if (claimed == null) {
                break;
            }
            claimedEvents.add(this.toOutboxRecord(claimed));
        }

        return claimedEvents;
    }

    @Override
    public void markSent(final String outboxEventId) {
        final Query query = Query.query(Criteria.where("_id").is(this.objectIdOrString(outboxEventId)));
        final Update update = new Update()
                .set("status", OutboxStatus.SENT.name())
                .set("updatedAt", new Date())
                .set("lastError", null)
                .unset("lockUntil");

        this.mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    @Override
    public void markFailed(final String outboxEventId,
                           final String errorMessage,
                           final Duration retryDelay,
                           final int maxRetries,
                           final Instant now) {
        final Query query = Query.query(Criteria.where("_id").is(this.objectIdOrString(outboxEventId)));
        final Document current = this.mongoTemplate.findOne(query, Document.class, COLLECTION_NAME);
        if (current == null) {
            return;
        }

        final int attempts = current.getInteger("attempts", 0) + 1;
        final OutboxStatus nextStatus = attempts >= maxRetries ? OutboxStatus.DEAD : OutboxStatus.FAILED;

        final Update update = new Update()
                .set("attempts", attempts)
                .set("status", nextStatus.name())
                .set("lastError", errorMessage)
                .set("nextAttemptAt", Date.from(now.plus(retryDelay)))
                .set("updatedAt", Date.from(now))
                .unset("lockUntil");

        this.mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    @Override
    public int deleteSentBefore(final Instant cutoff) {
        final Query query = Query.query(new Criteria().andOperator(
                Criteria.where("status").is(OutboxStatus.SENT.name()),
                Criteria.where("createdAt").lt(Date.from(cutoff))));
        return Math.toIntExact(this.mongoTemplate.remove(query, COLLECTION_NAME).getDeletedCount());
    }

    private OutboxRecord toOutboxRecord(final Document source) {
        final Date nextAttemptAt = source.getDate("nextAttemptAt");
        final Date createdAt = source.getDate("createdAt");
        final Date updatedAt = source.getDate("updatedAt");

        return OutboxRecord.builder()
                .id(this.asId(source.get("_id")))
                .eventType(source.getString("eventType"))
                .payload(source.getString("payload"))
                .headers(defaultMap(source.get("headers", Map.class)))
                .metadata(defaultMap(source.get("metadata", Map.class)))
                .traceId(source.getString("traceId"))
                .aggregateType(source.getString("aggregateType"))
                .aggregateId(source.getLong("aggregateId"))
                .initiatorType(source.getString("initiatorType"))
                .initiatorId(source.getString("initiatorId"))
                .status(OutboxStatus.valueOf(source.getString("status")))
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
}
