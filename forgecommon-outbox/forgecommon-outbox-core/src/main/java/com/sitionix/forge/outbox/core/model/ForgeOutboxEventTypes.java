package com.sitionix.forge.outbox.core.model;

import java.util.Set;

/**
 * Registry abstraction that resolves service-defined outbox event types by eventType description.
 */
public interface ForgeOutboxEventTypes {

    /**
     * Resolves event type configuration by transport-level description.
     *
     * @param description transport event type value
     * @return matching event type configuration
     */
    ForgeOutboxEventType byDescription(String description);

    /**
     * @return all event types supported by the service configuration
     */
    Set<String> supportedEventTypes();
}
