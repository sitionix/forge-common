package com.sitionix.forge.inbox.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.inbox.boot.codec.JacksonInboxPayloadCodec;
import com.sitionix.forge.inbox.boot.worker.ScheduledInboxCleanup;
import com.sitionix.forge.inbox.boot.worker.ScheduledInboxWorker;
import com.sitionix.forge.inbox.core.model.InboxWorkerPolicy;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import com.sitionix.forge.inbox.core.port.ForgeInboxPayload;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import com.sitionix.forge.inbox.core.port.InboxPayloadCodec;
import com.sitionix.forge.inbox.core.port.InboxHandler;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import com.sitionix.forge.inbox.core.service.CompositeInboxHandler;
import com.sitionix.forge.inbox.core.service.DefaultForgeInbox;
import com.sitionix.forge.inbox.core.service.InboxDispatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

@AutoConfiguration(afterName = {
        "com.sitionix.forge.inbox.postgres.config.ForgeInboxPostgresAutoConfiguration",
        "com.sitionix.forge.inbox.mongo.config.ForgeInboxMongoAutoConfiguration"
})
@EnableScheduling
@EnableConfigurationProperties(ForgeInboxProperties.class)
@ConditionalOnProperty(prefix = "forge.inbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ForgeInboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock forgeInboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ObjectMapper.class)
    public InboxPayloadCodec inboxPayloadCodec(final ObjectMapper objectMapper) {
        return new JacksonInboxPayloadCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxStartupValidator inboxStartupValidator(final ForgeInboxProperties properties,
                                                       final ObjectProvider<InboxStorage> inboxStorageProvider,
                                                       final ObjectProvider<DataSource> dataSourceProvider,
                                                       final ListableBeanFactory beanFactory) {
        return new InboxStartupValidator(properties, inboxStorageProvider, dataSourceProvider, beanFactory);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(InboxStorage.class)
    static class StorageBackedInboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ForgeInbox<ForgeInboxPayload> forgeInbox(final InboxStorage inboxStorage,
                                                           final Clock forgeInboxClock,
                                                           final InboxPayloadCodec inboxPayloadCodec) {
            return new DefaultForgeInbox<>(inboxStorage, forgeInboxClock, inboxPayloadCodec);
        }

        @Bean
        @ConditionalOnMissingBean(InboxHandler.class)
        public InboxHandler inboxHandler(final ObjectProvider<ForgeInboxEventHandler<?>> handlersProvider,
                                         final InboxPayloadCodec inboxPayloadCodec) {
            final List<ForgeInboxEventHandler<?>> handlers = handlersProvider.orderedStream().toList();
            return new CompositeInboxHandler(handlers, inboxPayloadCodec);
        }

        @Bean
        @ConditionalOnBean(InboxHandler.class)
        @ConditionalOnMissingBean
        public InboxWorkerPolicy inboxWorkerPolicy(final ForgeInboxProperties properties) {
            return InboxWorkerPolicy.builder()
                    .batchSize(properties.getWorker().getBatchSize())
                    .retryDelay(properties.getWorker().getRetryDelay())
                    .maxRetries(properties.getWorker().getMaxRetries())
                    .lockEnabled(properties.getWorker().getLock().isEnabled())
                    .lockLease(properties.getWorker().getLock().getLease())
                    .build();
        }

        @Bean
        @ConditionalOnBean({InboxHandler.class, InboxWorkerPolicy.class})
        @ConditionalOnMissingBean
        public InboxDispatcher inboxDispatcher(final InboxStorage inboxStorage,
                                               final InboxHandler inboxHandler,
                                               final InboxWorkerPolicy inboxWorkerPolicy,
                                               final Clock forgeInboxClock) {
            return new InboxDispatcher(inboxStorage, inboxHandler, inboxWorkerPolicy, forgeInboxClock);
        }

        @Bean
        @ConditionalOnBean(InboxDispatcher.class)
        @ConditionalOnMissingBean
        public ForgeInboxWorker forgeInboxWorker(final InboxDispatcher inboxDispatcher) {
            return inboxDispatcher::dispatchPendingEvents;
        }

        @Bean
        @ConditionalOnBean(ForgeInboxWorker.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "forge.inbox.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
        public ScheduledInboxWorker scheduledInboxWorker(final ForgeInboxProperties properties,
                                                         final ForgeInboxWorker forgeInboxWorker) {
            return new ScheduledInboxWorker(properties, forgeInboxWorker);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "forge.inbox.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
        public ScheduledInboxCleanup scheduledInboxCleanup(final ForgeInboxProperties properties,
                                                           final InboxStorage inboxStorage,
                                                           final Clock forgeInboxClock) {
            return new ScheduledInboxCleanup(properties, inboxStorage, forgeInboxClock);
        }
    }
}
