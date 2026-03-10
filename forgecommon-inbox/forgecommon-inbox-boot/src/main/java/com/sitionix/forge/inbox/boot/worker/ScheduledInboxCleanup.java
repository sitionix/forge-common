package com.sitionix.forge.inbox.boot.worker;

import com.sitionix.forge.inbox.boot.config.ForgeInboxProperties;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class ScheduledInboxCleanup implements SchedulingConfigurer {

    private final ForgeInboxProperties properties;
    private final InboxStorage inboxStorage;
    private final Clock forgeInboxClock;

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        if (!this.properties.isEnabled() || !this.properties.getCleanup().isEnabled()) {
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
        final int deleted = this.inboxStorage.deleteProcessedBefore(cutoff);
        if (deleted > 0) {
            log.info("ForgeInbox cleanup deleted processed events: {}", deleted);
        }
        return deleted;
    }
}
