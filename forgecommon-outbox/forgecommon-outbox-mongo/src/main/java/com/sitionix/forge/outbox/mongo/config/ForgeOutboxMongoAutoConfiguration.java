package com.sitionix.forge.outbox.mongo.config;

import com.sitionix.forge.outbox.core.port.OutboxStorage;
import com.sitionix.forge.outbox.mongo.storage.MongoOutboxIndexesInitializer;
import com.sitionix.forge.outbox.mongo.storage.MongoOutboxStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

@AutoConfiguration
public class ForgeOutboxMongoAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnExpression("'${forge.outbox.domain-store:NONE}'.equalsIgnoreCase('MONGO')")
    @ConditionalOnMissingBean(OutboxStorage.class)
    public OutboxStorage mongoOutboxStorage(final MongoTemplate mongoTemplate) {
        return new MongoOutboxStorage(mongoTemplate);
    }

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnExpression("'${forge.outbox.domain-store:NONE}'.equalsIgnoreCase('NONE')")
    @ConditionalOnMissingBean(value = OutboxStorage.class, type = "javax.sql.DataSource")
    public OutboxStorage mongoOutboxStorageByAutoDetection(final MongoTemplate mongoTemplate) {
        return new MongoOutboxStorage(mongoTemplate);
    }

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean
    public MongoOutboxIndexesInitializer mongoOutboxIndexesInitializer(final MongoTemplate mongoTemplate) {
        return new MongoOutboxIndexesInitializer(mongoTemplate);
    }
}
