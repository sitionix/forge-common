package com.sitionix.forge.inbox.postgres.it.support;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

public record AggregateInboxPayload(String value) implements ForgeInboxPayload {
}
