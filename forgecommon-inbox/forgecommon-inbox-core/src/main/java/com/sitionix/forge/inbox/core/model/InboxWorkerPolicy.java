package com.sitionix.forge.inbox.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

@Getter
@Builder
public class InboxWorkerPolicy {

    private final int batchSize;
    private final Duration retryDelay;
    private final int maxRetries;
    private final boolean lockEnabled;
    private final Duration lockLease;
}
