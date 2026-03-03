package com.sitionix.forge.outbox.boot.worker;

import com.sitionix.forge.outbox.boot.config.ForgeOutboxProperties;
import com.sitionix.forge.outbox.core.model.OutboxDispatchSummary;
import com.sitionix.forge.outbox.core.port.ForgeOutboxWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Slf4j
@RequiredArgsConstructor
public class ScheduledOutboxWorker implements SchedulingConfigurer {

    private final ForgeOutboxProperties properties;
    private final ForgeOutboxWorker forgeOutboxWorker;

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        if (!this.properties.isEnabled() || !this.properties.getWorker().isEnabled()) {
            return;
        }

        taskRegistrar.addFixedDelayTask(this::dispatchPendingEvents, this.properties.getWorker().getFixedDelay());
    }

    private void dispatchPendingEvents() {
        final OutboxDispatchSummary summary = this.forgeOutboxWorker.dispatchPendingEvents();
        if (summary.getClaimed() == 0) {
            return;
        }
        log.info("ForgeOutbox batch processed: claimed={}, sent={}, failed={}",
                summary.getClaimed(),
                summary.getSent(),
                summary.getFailed());
    }
}
