package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Persistent store for outbox records.
 */
public interface OutboxStorage {

    /**
     * Persists a new outbox record.
     *
     * @param record record to persist
     */
    void enqueue(OutboxRecord record);

    /**
     * Claims records for dispatch and marks them as in-progress.
     *
     * @param eventStatuses statuses eligible for claiming
     * @param eventTypes supported event types
     * @param batchSize maximum number of records to claim
     * @param now current instant
     * @param lockEnabled whether visibility locking is enabled
     * @param lockLease lock lease duration
     * @return claimed records
     */
    List<OutboxRecord> claimPendingEvents(Set<OutboxStatus> eventStatuses,
                                          Set<String> eventTypes,
                                          int batchSize,
                                          Instant now,
                                          boolean lockEnabled,
                                          Duration lockLease);

    /**
     * Marks an in-progress record as sent.
     *
     * @param outboxEventId record id
     * @param now current instant
     * @param expectedUpdatedAt optimistic concurrency marker from claim operation
     */
    void markSent(String outboxEventId, Instant now, Instant expectedUpdatedAt);

    /**
     * Marks an in-progress record as failed or dead depending on retry policy.
     *
     * @param outboxEventId record id
     * @param errorMessage failure message
     * @param retryDelay delay before next retry
     * @param maxRetries retries limit before dead state
     * @param now current instant
     * @param expectedUpdatedAt optimistic concurrency marker from claim operation
     */
    void markFailed(String outboxEventId,
                    String errorMessage,
                    Duration retryDelay,
                    int maxRetries,
                    Instant now,
                    Instant expectedUpdatedAt);

    /**
     * Deletes sent records older than cutoff.
     *
     * @param cutoff retention cutoff
     * @return number of deleted records
     */
    int deleteSentBefore(Instant cutoff);
}
