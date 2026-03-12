package com.sitionix.forge.inbox.boot.worker;

import com.sitionix.forge.inbox.boot.config.ForgeInboxProperties;
import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;
import java.util.Objects;

@Slf4j
public class ScheduledInboxWorker implements SchedulingConfigurer {

    private final ForgeInboxProperties properties;
    private final ForgeInboxWorker forgeInboxWorker;
    private final InboxSchemaAvailabilityGuard schemaAvailabilityGuard;

    public ScheduledInboxWorker(final ForgeInboxProperties properties,
                                final ForgeInboxWorker forgeInboxWorker,
                                final Clock forgeInboxClock) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.forgeInboxWorker = Objects.requireNonNull(forgeInboxWorker, "forgeInboxWorker is required");
        final ForgeInboxProperties.Startup startup = Objects.requireNonNull(this.properties.getStartup(), "properties.startup is required");
        this.schemaAvailabilityGuard = new InboxSchemaAvailabilityGuard(
                log,
                "worker",
                Objects.requireNonNull(startup.getSchemaReadyTimeout(), "properties.startup.schemaReadyTimeout is required"),
                Objects.requireNonNull(forgeInboxClock, "forgeInboxClock is required"));
    }

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        if (!this.properties.isEnabled() || !this.properties.getWorker().isEnabled()) {
            return;
        }

        taskRegistrar.addFixedDelayTask(this::dispatchPendingEvents, this.properties.getWorker().getFixedDelay());
    }

    void dispatchPendingEvents() {
        final InboxDispatchSummary summary;
        try {
            summary = this.forgeInboxWorker.dispatchPendingEvents();
            this.schemaAvailabilityGuard.onSuccess();
        } catch (final DataAccessException exception) {
            this.schemaAvailabilityGuard.onFailure(exception);
            return;
        }

        if (summary.getClaimed() == 0) {
            return;
        }
        log.info("ForgeInbox batch processed: claimed={}, processed={}, failed={}",
                summary.getClaimed(),
                summary.getProcessed(),
                summary.getFailed());
    }
}
