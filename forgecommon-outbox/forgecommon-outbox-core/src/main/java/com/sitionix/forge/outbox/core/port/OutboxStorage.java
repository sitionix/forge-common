package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface OutboxStorage {

    void enqueue(OutboxRecord record);

    List<OutboxRecord> claimPendingEvents(Set<OutboxStatus> eventStatuses,
                                          Set<String> eventTypes,
                                          int batchSize,
                                          Instant now,
                                          boolean lockEnabled,
                                          Duration lockLease);

    void markSent(String outboxEventId);

    void markFailed(String outboxEventId,
                    String errorMessage,
                    Duration retryDelay,
                    int maxRetries,
                    Instant now);

    int deleteSentBefore(Instant cutoff);
}
