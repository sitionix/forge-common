package com.sitionix.forge.outbox.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

@Getter
@Builder
public class OutboxWorkerPolicy {

    private final int batchSize;
    private final Duration retryDelay;
    private final int maxRetries;
    private final boolean lockEnabled;
    private final Duration lockLease;
}
