package com.sitionix.forge.inbox.core.model;

import java.util.Set;

/**
 * Registry abstraction that resolves service-defined inbox event types by eventType description.
 */
public interface ForgeInboxEventTypes {

    /**
     * Resolves event type configuration by transport-level description.
     *
     * @param description transport event type value
     * @return matching event type configuration
     */
    ForgeInboxEventType byDescription(String description);

    /**
     * @return all event types supported by the service configuration
     */
    Set<String> supportedEventTypes();
}
