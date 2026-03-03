package com.sitionix.forge.outbox.core.port;

public interface ForgeOutbox<P extends ForgeOutboxPayload> {

    void send(P payload);
}
