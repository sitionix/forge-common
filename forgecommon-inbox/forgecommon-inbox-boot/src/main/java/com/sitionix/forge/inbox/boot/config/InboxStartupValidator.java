package com.sitionix.forge.inbox.boot.config;

import com.sitionix.forge.inbox.core.model.InboxDomainStore;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;

import javax.sql.DataSource;
import java.util.Objects;

public class InboxStartupValidator implements InitializingBean {

    private static final String MONGO_TEMPLATE_CLASS_NAME = "org.springframework.data.mongodb.core.MongoTemplate";

    private final ForgeInboxProperties properties;
    private final ObjectProvider<InboxStorage> inboxStorageProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<ForgeInboxEventTypes> eventTypesProvider;
    private final ListableBeanFactory beanFactory;

    public InboxStartupValidator(final ForgeInboxProperties properties,
                                  final ObjectProvider<InboxStorage> inboxStorageProvider,
                                  final ObjectProvider<DataSource> dataSourceProvider,
                                  final ObjectProvider<ForgeInboxEventTypes> eventTypesProvider,
                                  final ListableBeanFactory beanFactory) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.inboxStorageProvider = Objects.requireNonNull(inboxStorageProvider, "inboxStorageProvider is required");
        this.dataSourceProvider = Objects.requireNonNull(dataSourceProvider, "dataSourceProvider is required");
        this.eventTypesProvider = Objects.requireNonNull(eventTypesProvider, "eventTypesProvider is required");
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory is required");
    }

    @Override
    public void afterPropertiesSet() {
        if (!this.properties.isEnabled()) {
            return;
        }

        final InboxStorage inboxStorage = this.inboxStorageProvider.getIfAvailable();
        final boolean hasDataSource = this.dataSourceProvider.getIfAvailable() != null;
        final boolean hasMongoTemplate = this.hasBeanOfType(MONGO_TEMPLATE_CLASS_NAME);

        if (InboxDomainStore.NONE.equals(this.properties.getDomainStore()) && hasDataSource && hasMongoTemplate) {
            throw new IllegalStateException(
                    "Forge Inbox auto-detection is ambiguous for domain-store=NONE when both DataSource and MongoTemplate are present. "
                            + "Set forge.inbox.domain-store explicitly to POSTGRES or MONGO.");
        }

        if (inboxStorage == null
                && InboxDomainStore.NONE.equals(this.properties.getDomainStore())
                && (hasDataSource || hasMongoTemplate)) {
            throw new IllegalStateException(
                    "Forge Inbox auto-detection could not resolve InboxStorage for domain-store=NONE. "
                            + "Set forge.inbox.domain-store explicitly to POSTGRES or MONGO.");
        }

        if (inboxStorage == null && !InboxDomainStore.NONE.equals(this.properties.getDomainStore())) {
            throw new IllegalStateException(
                    "Forge Inbox is enabled but no InboxStorage bean is configured for domain-store="
                            + this.properties.getDomainStore());
        }

        final boolean workerEnabled = this.properties.getWorker().isEnabled();
        if (workerEnabled && inboxStorage != null && this.eventTypesProvider.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "Forge Inbox is enabled but no ForgeInboxEventTypes bean is configured. "
                            + "Define a service-level event-type registry bean (for example EnumForgeInboxEventTypes).");
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
