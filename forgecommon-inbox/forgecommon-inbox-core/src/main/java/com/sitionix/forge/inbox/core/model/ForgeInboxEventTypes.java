package com.sitionix.forge.inbox.core.model;

import java.util.Set;

/**
 * Registry abstraction that resolves service-defined inbox event types by eventType description.
 */
public interface ForgeInboxEventTypes {

    ForgeInboxEventType byDescription(String description);

    Set<String> supportedEventTypes();
}
