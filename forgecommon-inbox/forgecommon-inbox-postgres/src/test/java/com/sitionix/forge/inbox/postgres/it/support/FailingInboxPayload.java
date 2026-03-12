package com.sitionix.forge.inbox.postgres.it.support;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

public record FailingInboxPayload(String value) implements ForgeInboxPayload {
}
