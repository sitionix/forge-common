package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxDispatchSummary;

@FunctionalInterface
public interface ForgeOutboxWorker {

    OutboxDispatchSummary dispatchPendingEvents();
}
