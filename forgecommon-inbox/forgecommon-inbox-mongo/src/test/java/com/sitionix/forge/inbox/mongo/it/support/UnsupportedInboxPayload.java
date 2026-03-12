package com.sitionix.forge.inbox.mongo.it.support;

import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;

public record UnsupportedInboxPayload(String value) implements ForgeInboxPayload {
}
