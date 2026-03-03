package com.sitionix.forge.outbox.core.port;

import com.sitionix.forge.outbox.core.model.OutboxRecord;

import java.util.Set;

public interface OutboxPublisher {

    Set<String> supportedEventTypes();

    void publish(OutboxRecord record) throws Exception;
}
