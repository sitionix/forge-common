package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Persistent store for inbox records.
 */
public interface InboxStorage {

    /**
     * Persists a new inbox record.
     *
     * @param record record to persist
     */
    void enqueue(InboxRecord record);

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
    List<InboxRecord> claimPendingEvents(Set<InboxStatus> eventStatuses,
                                          Set<String> eventTypes,
                                          int batchSize,
                                          Instant now,
                                          boolean lockEnabled,
                                          Duration lockLease);

    /**
     * Marks an in-progress record as processed.
     *
     * @param inboxEventId record id
     * @param now current instant
     * @param expectedUpdatedAt optimistic concurrency marker from claim operation
     */
    void markProcessed(String inboxEventId, Instant now, Instant expectedUpdatedAt);

    /**
     * Marks an in-progress record as failed or dead depending on retry policy.
     *
     * @param inboxEventId record id
     * @param errorMessage failure message
     * @param retryDelay delay before next retry
     * @param maxRetries retries limit before dead state
     * @param now current instant
     * @param expectedUpdatedAt optimistic concurrency marker from claim operation
     */
    void markFailed(String inboxEventId,
                    String errorMessage,
                    Duration retryDelay,
                    int maxRetries,
                    Instant now,
                    Instant expectedUpdatedAt);

    /**
     * Deletes processed records older than cutoff.
     *
     * @param cutoff retention cutoff
     * @return number of deleted records
     */
    int deleteProcessedBefore(Instant cutoff);
}
