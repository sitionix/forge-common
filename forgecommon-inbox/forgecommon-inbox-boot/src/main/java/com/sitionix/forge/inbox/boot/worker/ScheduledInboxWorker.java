package com.sitionix.forge.inbox.boot.worker;

import com.sitionix.forge.inbox.boot.config.ForgeInboxProperties;
import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Slf4j
@RequiredArgsConstructor
public class ScheduledInboxWorker implements SchedulingConfigurer {

    private final ForgeInboxProperties properties;
    private final ForgeInboxWorker forgeInboxWorker;

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        if (!this.properties.isEnabled() || !this.properties.getWorker().isEnabled()) {
            return;
        }

        taskRegistrar.addFixedDelayTask(this::dispatchPendingEvents, this.properties.getWorker().getFixedDelay());
    }

    private void dispatchPendingEvents() {
        final InboxDispatchSummary summary = this.forgeInboxWorker.dispatchPendingEvents();
        if (summary.getClaimed() == 0) {
            return;
        }
        log.info("ForgeInbox batch processed: claimed={}, processed={}, failed={}",
                summary.getClaimed(),
                summary.getProcessed(),
                summary.getFailed());
    }
}
