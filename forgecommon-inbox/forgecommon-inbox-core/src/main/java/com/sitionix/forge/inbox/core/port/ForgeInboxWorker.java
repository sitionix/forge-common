package com.sitionix.forge.inbox.core.port;

import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;

/**
 * Triggers one inbox dispatch cycle.
 */
@FunctionalInterface
public interface ForgeInboxWorker {

    InboxDispatchSummary dispatchPendingEvents();
}
