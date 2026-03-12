package com.sitionix.forge.inbox.mongo.it.support;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

public record SuccessInboxPayload(String value) implements ForgeInboxPayload {
}
