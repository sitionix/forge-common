package com.sitionix.forge.outbox.boot.config;

import com.sitionix.forge.outbox.core.model.OutboxDomainStore;
import com.sitionix.forge.outbox.core.model.ForgeOutboxEventTypes;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.util.Objects;

public class OutboxStartupValidator implements InitializingBean {

    private static final String MONGO_TEMPLATE_CLASS_NAME = "org.springframework.data.mongodb.core.MongoTemplate";

    private final ForgeOutboxProperties properties;
    private final ObjectProvider<OutboxStorage> outboxStorageProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<ForgeOutboxEventTypes> eventTypesProvider;
    private final ObjectProvider<OutboxPublisher> outboxPublisherProvider;
    private final ListableBeanFactory beanFactory;

    public OutboxStartupValidator(final ForgeOutboxProperties properties,
                                  final ObjectProvider<OutboxStorage> outboxStorageProvider,
                                  final ObjectProvider<DataSource> dataSourceProvider,
                                  final ObjectProvider<ForgeOutboxEventTypes> eventTypesProvider,
                                  final ObjectProvider<OutboxPublisher> outboxPublisherProvider,
                                  final ListableBeanFactory beanFactory) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.outboxStorageProvider = Objects.requireNonNull(outboxStorageProvider, "outboxStorageProvider is required");
        this.dataSourceProvider = Objects.requireNonNull(dataSourceProvider, "dataSourceProvider is required");
        this.eventTypesProvider = Objects.requireNonNull(eventTypesProvider, "eventTypesProvider is required");
        this.outboxPublisherProvider = Objects.requireNonNull(outboxPublisherProvider, "outboxPublisherProvider is required");
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

        final boolean workerEnabled = this.properties.getWorker().isEnabled();
        if (workerEnabled
                && outboxStorage != null
                && this.eventTypesProvider.getIfAvailable() == null
                && this.outboxPublisherProvider.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "Forge Outbox is enabled but neither ForgeOutboxEventTypes nor OutboxPublisher bean is configured. "
                            + "Define a service-level event-type registry bean (for example EnumForgeOutboxEventTypes) "
                            + "or provide a custom OutboxPublisher.");
        }
    }

    private boolean hasBeanOfType(final String className) {
        final ClassLoader classLoader = this.resolveClassLoader();
        if (!ClassUtils.isPresent(className, classLoader)) {
            return false;
        }
        final Class<?> type = ClassUtils.resolveClassName(className, classLoader);
        return this.beanFactory.getBeanNamesForType(type).length > 0;
    }

    private ClassLoader resolveClassLoader() {
        if (this.beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory
                && configurableBeanFactory.getBeanClassLoader() != null) {
            return configurableBeanFactory.getBeanClassLoader();
        }
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader != null ? contextClassLoader : OutboxStartupValidator.class.getClassLoader();
    }
}
