package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.model.InboxWorkerPolicy;
import com.sitionix.forge.inbox.core.port.InboxHandler;
import com.sitionix.forge.inbox.core.port.InboxStorage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class InboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final InboxStorage storage;
    private final InboxHandler handler;
    private final InboxWorkerPolicy policy;
    private final Clock clock;

    public InboxDispatcher(final InboxStorage storage,
                           final InboxHandler handler,
                           final InboxWorkerPolicy policy,
                           final Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage is required");
        this.handler = Objects.requireNonNull(handler, "handler is required");
        this.policy = Objects.requireNonNull(policy, "policy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.validatePolicy(this.policy);
    }

    public InboxDispatchSummary dispatchPendingEvents() {
        final Instant now = Instant.now(this.clock);
        final Set<String> supportedEventTypes = this.handler.supportedEventTypes();
        if (supportedEventTypes == null || supportedEventTypes.isEmpty()) {
            return InboxDispatchSummary.empty();
        }
        final List<InboxRecord> events = this.storage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING, InboxStatus.FAILED),
                supportedEventTypes,
                this.policy.getBatchSize(),
                now,
                this.policy.isLockEnabled(),
                this.policy.getLockLease());

        if (events.isEmpty()) {
            return InboxDispatchSummary.empty();
        }

        final int processed = (int) events.stream()
                .filter(event -> this.processEvent(event, now))
                .count();
        final int failed = events.size() - processed;

        return InboxDispatchSummary.builder()
                .claimed(events.size())
                .processed(processed)
                .failed(failed)
                .build();
    }

    private boolean processEvent(final InboxRecord event,
                                 final Instant now) {
        try {
            this.handler.handle(event);
            this.storage.markProcessed(event.getId(), now, event.getUpdatedAt());
            return true;
        } catch (final Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            this.storage.markFailed(
                    event.getId(),
                    this.formatErrorMessage(exception),
                    this.policy.getRetryDelay(),
                    this.policy.getMaxRetries(),
                    now,
                    event.getUpdatedAt());
            return false;
        }
    }

    private String formatErrorMessage(final Exception exception) {
        final String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH)
                : message;
    }

    private void validatePolicy(final InboxWorkerPolicy inboxWorkerPolicy) {
        if (inboxWorkerPolicy.getBatchSize() < 1) {
            throw new IllegalArgumentException("policy.batchSize must be greater than 0");
        }
        if (inboxWorkerPolicy.getMaxRetries() < 1) {
            throw new IllegalArgumentException("policy.maxRetries must be greater than 0");
        }
        final Duration retryDelay = Objects.requireNonNull(inboxWorkerPolicy.getRetryDelay(), "policy.retryDelay is required");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("policy.retryDelay must be greater than or equal to 0");
        }
        if (inboxWorkerPolicy.isLockEnabled()) {
            final Duration lockLease = Objects.requireNonNull(inboxWorkerPolicy.getLockLease(), "policy.lockLease is required");
            if (lockLease.isNegative() || lockLease.isZero()) {
                throw new IllegalArgumentException("policy.lockLease must be greater than 0 when lock is enabled");
            }
        }
    }
}
