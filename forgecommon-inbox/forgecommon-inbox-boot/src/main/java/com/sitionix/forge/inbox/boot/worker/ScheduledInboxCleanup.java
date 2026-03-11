package com.sitionix.forge.inbox.boot.worker;

import com.sitionix.forge.inbox.boot.config.ForgeInboxProperties;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Slf4j
public class ScheduledInboxCleanup implements SchedulingConfigurer {

    private final ForgeInboxProperties properties;
    private final InboxStorage inboxStorage;
    private final Clock forgeInboxClock;
    private final InboxSchemaAvailabilityGuard schemaAvailabilityGuard;

    public ScheduledInboxCleanup(final ForgeInboxProperties properties,
                                 final InboxStorage inboxStorage,
                                 final Clock forgeInboxClock) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.inboxStorage = Objects.requireNonNull(inboxStorage, "inboxStorage is required");
        this.forgeInboxClock = Objects.requireNonNull(forgeInboxClock, "forgeInboxClock is required");
        final ForgeInboxProperties.Startup startup = Objects.requireNonNull(this.properties.getStartup(), "properties.startup is required");
        this.schemaAvailabilityGuard = new InboxSchemaAvailabilityGuard(
                log,
                "cleanup",
                Objects.requireNonNull(startup.getSchemaReadyTimeout(), "properties.startup.schemaReadyTimeout is required"),
                this.forgeInboxClock);
    }

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        if (!this.properties.isEnabled()
                || !this.properties.getWorker().isEnabled()
                || !this.properties.getCleanup().isEnabled()) {
            return;
        }
        taskRegistrar.addFixedDelayTask(this::cleanupProcessedEvents, this.properties.getCleanup().getFixedDelay());
    }

    public int cleanupProcessedEvents() {
        final Duration retention = this.properties.getCleanup().getRetention();
        if (retention == null || retention.isNegative() || retention.isZero()) {
            return 0;
        }

        final Instant now = this.forgeInboxClock.instant();
        final Instant cutoff = now.minus(retention);
        final int deleted;
        try {
            deleted = this.inboxStorage.deleteProcessedBefore(cutoff);
            this.schemaAvailabilityGuard.onSuccess();
        } catch (final DataAccessException exception) {
            this.schemaAvailabilityGuard.onFailure(exception, now);
            return 0;
        }

        if (deleted > 0) {
            log.info("ForgeInbox cleanup deleted processed events: {}", deleted);
        }
        return deleted;
    }
}
