package com.sitionix.forge.inbox.mongo.config;

import com.sitionix.forge.inbox.core.port.InboxStorage;
import com.sitionix.forge.inbox.mongo.storage.MongoInboxIndexesInitializer;
import com.sitionix.forge.inbox.mongo.storage.MongoInboxStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

@AutoConfiguration(after = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
public class ForgeInboxMongoAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean
    public MongoInboxIndexesInitializer mongoInboxIndexesInitializer(final MongoTemplate mongoTemplate) {
        return new MongoInboxIndexesInitializer(mongoTemplate);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnProperty(prefix = "forge.inbox", name = "domain-store", havingValue = "MONGO")
    @ConditionalOnMissingBean(InboxStorage.class)
    static class ExplicitMongoInboxStorageConfiguration {

        @Bean
        public InboxStorage mongoInboxStorage(final MongoTemplate mongoTemplate) {
            return new MongoInboxStorage(mongoTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnProperty(prefix = "forge.inbox", name = "domain-store", havingValue = "NONE", matchIfMissing = true)
    @ConditionalOnMissingBean({InboxStorage.class, DataSource.class})
    static class AutoDetectedMongoInboxStorageConfiguration {

        @Bean
        public InboxStorage mongoInboxStorageByAutoDetection(final MongoTemplate mongoTemplate) {
            return new MongoInboxStorage(mongoTemplate);
        }
    }
}
