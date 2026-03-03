package com.sitionix.forge.outbox.boot.config;

import com.sitionix.forge.outbox.core.model.OutboxDomainStore;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

public class OutboxStartupValidator implements InitializingBean {

    private final ForgeOutboxProperties properties;
    private final ObjectProvider<OutboxStorage> outboxStorageProvider;

    public OutboxStartupValidator(final ForgeOutboxProperties properties,
                                  final ObjectProvider<OutboxStorage> outboxStorageProvider) {
        this.properties = properties;
        this.outboxStorageProvider = outboxStorageProvider;
    }

    @Override
    public void afterPropertiesSet() {
        if (!this.properties.isEnabled()) {
            return;
        }

        final OutboxStorage outboxStorage = this.outboxStorageProvider.getIfAvailable();
        if (outboxStorage == null && !OutboxDomainStore.NONE.equals(this.properties.getDomainStore())) {
            throw new IllegalStateException(
                    "Forge Outbox is enabled but no OutboxStorage bean is configured for domain-store="
                            + this.properties.getDomainStore());
        }
    }
}
