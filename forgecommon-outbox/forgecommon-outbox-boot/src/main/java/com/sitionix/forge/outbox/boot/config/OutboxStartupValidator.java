package com.sitionix.forge.outbox.boot.config;

import com.sitionix.forge.outbox.core.model.OutboxDomainStore;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;

import javax.sql.DataSource;
import java.util.Objects;

public class OutboxStartupValidator implements InitializingBean {

    private static final String MONGO_TEMPLATE_CLASS_NAME = "org.springframework.data.mongodb.core.MongoTemplate";

    private final ForgeOutboxProperties properties;
    private final ObjectProvider<OutboxStorage> outboxStorageProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ListableBeanFactory beanFactory;

    public OutboxStartupValidator(final ForgeOutboxProperties properties,
                                  final ObjectProvider<OutboxStorage> outboxStorageProvider,
                                  final ObjectProvider<DataSource> dataSourceProvider,
                                  final ListableBeanFactory beanFactory) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.outboxStorageProvider = Objects.requireNonNull(outboxStorageProvider, "outboxStorageProvider is required");
        this.dataSourceProvider = Objects.requireNonNull(dataSourceProvider, "dataSourceProvider is required");
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory is required");
    }

    @Override
    public void afterPropertiesSet() {
        if (!this.properties.isEnabled()) {
            return;
        }

        final OutboxStorage outboxStorage = this.outboxStorageProvider.getIfAvailable();
        final boolean hasDataSource = this.dataSourceProvider.getIfAvailable() != null;
        final boolean hasMongoTemplate = this.hasBeanOfType(MONGO_TEMPLATE_CLASS_NAME);

        if (OutboxDomainStore.NONE.equals(this.properties.getDomainStore()) && hasDataSource && hasMongoTemplate) {
            throw new IllegalStateException(
                    "Forge Outbox auto-detection is ambiguous for domain-store=NONE when both DataSource and MongoTemplate are present. "
                            + "Set forge.outbox.domain-store explicitly to POSTGRES or MONGO.");
        }

        if (outboxStorage == null
                && OutboxDomainStore.NONE.equals(this.properties.getDomainStore())
                && (hasDataSource || hasMongoTemplate)) {
            throw new IllegalStateException(
                    "Forge Outbox auto-detection could not resolve OutboxStorage for domain-store=NONE. "
                            + "Set forge.outbox.domain-store explicitly to POSTGRES or MONGO.");
        }

        if (outboxStorage == null && !OutboxDomainStore.NONE.equals(this.properties.getDomainStore())) {
            throw new IllegalStateException(
                    "Forge Outbox is enabled but no OutboxStorage bean is configured for domain-store="
                            + this.properties.getDomainStore());
        }
    }

    private boolean hasBeanOfType(final String className) {
        try {
            final Class<?> type = Class.forName(className, false, this.beanFactory.getClass().getClassLoader());
            return this.beanFactory.getBeanNamesForType(type).length > 0;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }
}
