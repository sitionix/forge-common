package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxDispatchSummary;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.model.OutboxWorkerPolicy;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;
import com.sitionix.forge.outbox.core.port.OutboxStorage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class OutboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxStorage storage;
    private final OutboxPublisher publisher;
    private final OutboxWorkerPolicy policy;
    private final Clock clock;

    public OutboxDispatcher(final OutboxStorage storage,
                            final OutboxPublisher publisher,
                            final OutboxWorkerPolicy policy,
                            final Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage is required");
        this.publisher = Objects.requireNonNull(publisher, "publisher is required");
        this.policy = Objects.requireNonNull(policy, "policy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.validatePolicy(this.policy);
    }

    public OutboxDispatchSummary dispatchPendingEvents() {
        final Instant now = Instant.now(this.clock);
        final Set<String> supportedEventTypes = this.publisher.supportedEventTypes();
        if (supportedEventTypes == null || supportedEventTypes.isEmpty()) {
            return OutboxDispatchSummary.empty();
        }
        final List<OutboxRecord> events = this.storage.claimPendingEvents(
                EnumSet.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                supportedEventTypes,
                this.policy.getBatchSize(),
                now,
                this.policy.isLockEnabled(),
                this.policy.getLockLease());

        if (events.isEmpty()) {
            return OutboxDispatchSummary.empty();
        }

        int sent = 0;
        int failed = 0;

        for (final OutboxRecord event : events) {
            try {
                this.publisher.publish(event);
                this.storage.markSent(event.getId(), now, event.getUpdatedAt());
                sent++;
            } catch (final Exception exception) {
                this.storage.markFailed(
                        event.getId(),
                        this.formatErrorMessage(exception),
                        this.policy.getRetryDelay(),
                        this.policy.getMaxRetries(),
                        now,
                        event.getUpdatedAt());
                failed++;
            }
        }

        return OutboxDispatchSummary.builder()
                .claimed(events.size())
                .sent(sent)
                .failed(failed)
                .build();
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

    private void validatePolicy(final OutboxWorkerPolicy outboxWorkerPolicy) {
        if (outboxWorkerPolicy.getBatchSize() < 1) {
            throw new IllegalArgumentException("policy.batchSize must be greater than 0");
        }
        if (outboxWorkerPolicy.getMaxRetries() < 1) {
            throw new IllegalArgumentException("policy.maxRetries must be greater than 0");
        }
        final Duration retryDelay = Objects.requireNonNull(outboxWorkerPolicy.getRetryDelay(), "policy.retryDelay is required");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("policy.retryDelay must be greater than or equal to 0");
        }
        if (outboxWorkerPolicy.isLockEnabled()) {
            final Duration lockLease = Objects.requireNonNull(outboxWorkerPolicy.getLockLease(), "policy.lockLease is required");
            if (lockLease.isNegative() || lockLease.isZero()) {
                throw new IllegalArgumentException("policy.lockLease must be greater than 0 when lock is enabled");
            }
        }
    }
}
