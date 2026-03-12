package com.sitionix.forge.inbox.boot.worker;

import org.slf4j.Logger;
import org.springframework.dao.DataAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class InboxSchemaAvailabilityGuard {

    private final Logger logger;
    private final String componentName;
    private final Duration schemaReadyTimeout;
    private final Clock clock;
    private final AtomicBoolean missingSchemaLogged;
    private final AtomicReference<Instant> missingSchemaSince;

    InboxSchemaAvailabilityGuard(final Logger logger,
                                final String componentName,
                                final Duration schemaReadyTimeout,
                                final Clock clock) {
        this.logger = Objects.requireNonNull(logger, "logger is required");
        this.componentName = Objects.requireNonNull(componentName, "componentName is required");
        this.schemaReadyTimeout = Objects.requireNonNull(schemaReadyTimeout, "schemaReadyTimeout is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.missingSchemaLogged = new AtomicBoolean(false);
        this.missingSchemaSince = new AtomicReference<>();
    }

    void onSuccess() {
        this.missingSchemaLogged.set(false);
        this.missingSchemaSince.set(null);
    }

    void onFailure(final DataAccessException exception) {
        this.onFailure(exception, this.clock.instant());
    }

    void onFailure(final DataAccessException exception,
                   final Instant now) {
        if (!InboxStorageStartupGuard.isInboxSchemaMissing(exception)) {
            throw exception;
        }

        final Instant firstMissingAt = this.missingSchemaSince.updateAndGet(existing -> existing == null ? now : existing);
        final Duration elapsed = Duration.between(firstMissingAt, now);
        if (elapsed.compareTo(this.schemaReadyTimeout) >= 0) {
            throw new IllegalStateException(
                    "ForgeInbox %s failed: storage schema was not ready within %s"
                            .formatted(this.componentName, this.schemaReadyTimeout),
                    exception);
        }

        if (this.missingSchemaLogged.compareAndSet(false, true)) {
            this.logger.info("ForgeInbox {} skipped: storage schema is not ready yet", this.componentName);
        } else {
            this.logger.debug("ForgeInbox {} skipped: storage schema is still not ready", this.componentName);
        }
    }
}
