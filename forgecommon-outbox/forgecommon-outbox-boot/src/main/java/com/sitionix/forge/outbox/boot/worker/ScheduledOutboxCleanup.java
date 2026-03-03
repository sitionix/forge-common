package com.sitionix.forge.outbox.boot.worker;

import com.sitionix.forge.outbox.boot.config.ForgeOutboxProperties;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class ScheduledOutboxCleanup implements SchedulingConfigurer {

    private final ForgeOutboxProperties properties;
    private final OutboxStorage outboxStorage;
    private final Clock forgeOutboxClock;

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        if (!this.properties.isEnabled() || !this.properties.getCleanup().isEnabled()) {
            return;
        }
        taskRegistrar.addFixedDelayTask(this::cleanupSentEvents, this.properties.getCleanup().getFixedDelay());
    }

    public int cleanupSentEvents() {
        final Duration retention = this.properties.getCleanup().getRetention();
        if (retention == null || retention.isNegative() || retention.isZero()) {
            return 0;
        }

        final Instant now = this.forgeOutboxClock.instant();
        final Instant cutoff = now.minus(retention);
        final int deleted = this.outboxStorage.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("ForgeOutbox cleanup deleted sent events: {}", deleted);
        }
        return deleted;
    }
}
