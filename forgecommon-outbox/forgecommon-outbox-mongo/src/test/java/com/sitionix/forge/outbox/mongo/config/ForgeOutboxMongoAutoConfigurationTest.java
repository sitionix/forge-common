package com.sitionix.forge.outbox.mongo.config;

import com.sitionix.forge.outbox.core.port.OutboxStorage;
import com.sitionix.forge.outbox.mongo.storage.MongoOutboxStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgeOutboxMongoAutoConfigurationTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ForgeOutboxMongoAutoConfiguration.class));
    }

    @Test
    void givenDomainStoreMongoAndMongoTemplate_whenContextLoads_thenCreateOutboxStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.outbox.domain-store=MONGO")
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .run(context -> assertThat(context).hasSingleBean(OutboxStorage.class));
    }

    @Test
    void givenDomainStoreMissingAndMongoTemplateWithoutDataSource_whenContextLoads_thenCreateOutboxStorageByAutoDetection() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();

        //when
        //then
        this.contextRunner
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .run(context -> assertThat(context).hasSingleBean(OutboxStorage.class));
    }

    @Test
    void givenDomainStoreMissingAndMongoTemplateWithDataSource_whenContextLoads_thenSkipOutboxStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> assertThat(context).doesNotHaveBean(OutboxStorage.class));
    }

    @Test
    void givenDomainStorePostgresAndMongoTemplate_whenContextLoads_thenSkipOutboxStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.outbox.domain-store=POSTGRES")
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .run(context -> assertThat(context).doesNotHaveBean(OutboxStorage.class));
    }

    private MongoTemplate getMongoTemplate() {
        final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        final IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(MongoOutboxStorage.COLLECTION_NAME)).thenReturn(indexOperations);
        when(indexOperations.ensureIndex(any())).thenReturn("index");
        return mongoTemplate;
    }
}
