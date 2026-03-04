package com.sitionix.forge.outbox.mongo.config;

import com.sitionix.forge.outbox.core.port.OutboxStorage;
import com.sitionix.forge.outbox.mongo.storage.MongoOutboxIndexesInitializer;
import com.sitionix.forge.outbox.mongo.storage.MongoOutboxStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

@AutoConfiguration
public class ForgeOutboxMongoAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean
    public MongoOutboxIndexesInitializer mongoOutboxIndexesInitializer(final MongoTemplate mongoTemplate) {
        return new MongoOutboxIndexesInitializer(mongoTemplate);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnProperty(prefix = "forge.outbox", name = "domain-store", havingValue = "MONGO")
    @ConditionalOnMissingBean(OutboxStorage.class)
    static class ExplicitMongoOutboxStorageConfiguration {

        @Bean
        public OutboxStorage mongoOutboxStorage(final MongoTemplate mongoTemplate) {
            return new MongoOutboxStorage(mongoTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnProperty(prefix = "forge.outbox", name = "domain-store", havingValue = "NONE", matchIfMissing = true)
    @ConditionalOnMissingBean({OutboxStorage.class, DataSource.class})
    static class AutoDetectedMongoOutboxStorageConfiguration {

        @Bean
        public OutboxStorage mongoOutboxStorageByAutoDetection(final MongoTemplate mongoTemplate) {
            return new MongoOutboxStorage(mongoTemplate);
        }
    }
}
