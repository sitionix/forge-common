package com.sitionix.forge.outbox.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.outbox.boot.codec.JacksonOutboxPayloadCodec;
import com.sitionix.forge.outbox.boot.worker.ScheduledOutboxCleanup;
import com.sitionix.forge.outbox.boot.worker.ScheduledOutboxWorker;
import com.sitionix.forge.outbox.core.model.OutboxWorkerPolicy;
import com.sitionix.forge.outbox.core.port.ForgeOutbox;
import com.sitionix.forge.outbox.core.port.ForgeOutboxEventPublisher;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import com.sitionix.forge.outbox.core.port.ForgeOutboxWorker;
import com.sitionix.forge.outbox.core.port.OutboxPayloadCodec;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import com.sitionix.forge.outbox.core.service.CompositeOutboxPublisher;
import com.sitionix.forge.outbox.core.service.DefaultForgeOutbox;
import com.sitionix.forge.outbox.core.service.OutboxDispatcher;
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
        "com.sitionix.forge.outbox.postgres.config.ForgeOutboxPostgresAutoConfiguration",
        "com.sitionix.forge.outbox.mongo.config.ForgeOutboxMongoAutoConfiguration"
})
@EnableScheduling
@EnableConfigurationProperties(ForgeOutboxProperties.class)
@ConditionalOnProperty(prefix = "forge.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ForgeOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock forgeOutboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ObjectMapper.class)
    public OutboxPayloadCodec outboxPayloadCodec(final ObjectMapper objectMapper) {
        return new JacksonOutboxPayloadCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxStartupValidator outboxStartupValidator(final ForgeOutboxProperties properties,
                                                         final ObjectProvider<OutboxStorage> outboxStorageProvider,
                                                         final ObjectProvider<DataSource> dataSourceProvider,
                                                         final ListableBeanFactory beanFactory) {
        return new OutboxStartupValidator(properties, outboxStorageProvider, dataSourceProvider, beanFactory);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(OutboxStorage.class)
    static class StorageBackedOutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ForgeOutbox<ForgeOutboxPayload> forgeOutbox(final OutboxStorage outboxStorage,
                                                           final Clock forgeOutboxClock,
                                                           final OutboxPayloadCodec outboxPayloadCodec) {
            return new DefaultForgeOutbox<>(outboxStorage, forgeOutboxClock, outboxPayloadCodec);
        }

        @Bean
        @ConditionalOnMissingBean(OutboxPublisher.class)
        public OutboxPublisher outboxPublisher(final ObjectProvider<ForgeOutboxEventPublisher> publishersProvider,
                                              final OutboxPayloadCodec outboxPayloadCodec) {
            final List<ForgeOutboxEventPublisher> publishers = publishersProvider.orderedStream().toList();
            return new CompositeOutboxPublisher(publishers, outboxPayloadCodec);
        }

        @Bean
        @ConditionalOnBean(OutboxPublisher.class)
        @ConditionalOnMissingBean
        public OutboxWorkerPolicy outboxWorkerPolicy(final ForgeOutboxProperties properties) {
            return OutboxWorkerPolicy.builder()
                    .batchSize(properties.getWorker().getBatchSize())
                    .retryDelay(properties.getWorker().getRetryDelay())
                    .maxRetries(properties.getWorker().getMaxRetries())
                    .lockEnabled(properties.getWorker().getLock().isEnabled())
                    .lockLease(properties.getWorker().getLock().getLease())
                    .build();
        }

        @Bean
        @ConditionalOnBean({OutboxPublisher.class, OutboxWorkerPolicy.class})
        @ConditionalOnMissingBean
        public OutboxDispatcher outboxDispatcher(final OutboxStorage outboxStorage,
                                                 final OutboxPublisher outboxPublisher,
                                                 final OutboxWorkerPolicy outboxWorkerPolicy,
                                                 final Clock forgeOutboxClock) {
            return new OutboxDispatcher(outboxStorage, outboxPublisher, outboxWorkerPolicy, forgeOutboxClock);
        }

        @Bean
        @ConditionalOnBean(OutboxDispatcher.class)
        @ConditionalOnMissingBean
        public ForgeOutboxWorker forgeOutboxWorker(final OutboxDispatcher outboxDispatcher) {
            return outboxDispatcher::dispatchPendingEvents;
        }

        @Bean
        @ConditionalOnBean(ForgeOutboxWorker.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "forge.outbox.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
        public ScheduledOutboxWorker scheduledOutboxWorker(final ForgeOutboxProperties properties,
                                                           final ForgeOutboxWorker forgeOutboxWorker) {
            return new ScheduledOutboxWorker(properties, forgeOutboxWorker);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "forge.outbox.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
        public ScheduledOutboxCleanup scheduledOutboxCleanup(final ForgeOutboxProperties properties,
                                                             final OutboxStorage outboxStorage,
                                                             final Clock forgeOutboxClock) {
            return new ScheduledOutboxCleanup(properties, outboxStorage, forgeOutboxClock);
        }
    }
}
