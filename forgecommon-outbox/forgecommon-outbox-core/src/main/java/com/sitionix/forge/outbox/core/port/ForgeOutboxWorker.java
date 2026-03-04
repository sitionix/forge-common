package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxDispatchSummary;

/**
 * Triggers one outbox dispatch cycle.
 */
@FunctionalInterface
public interface ForgeOutboxWorker {

    OutboxDispatchSummary dispatchPendingEvents();
}
