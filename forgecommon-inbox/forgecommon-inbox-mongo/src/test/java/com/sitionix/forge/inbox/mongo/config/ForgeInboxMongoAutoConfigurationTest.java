package com.sitionix.forge.inbox.mongo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitionix.forge.inbox.boot.config.EnableInbox;
import com.sitionix.forge.inbox.core.port.ForgeInbox;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import com.sitionix.forge.inbox.mongo.storage.MongoInboxStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgeInboxMongoAutoConfigurationTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ForgeInboxMongoAutoConfiguration.class));
    }

    @Test
    void givenDomainStoreMongoAndMongoTemplate_whenContextLoads_thenCreateInboxStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.domain-store=MONGO")
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .run(context -> assertThat(context).hasSingleBean(InboxStorage.class));
    }

    @Test
    void givenDomainStoreMissingAndMongoTemplateWithoutDataSource_whenContextLoads_thenCreateInboxStorageByAutoDetection() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();

        //when
        //then
        this.contextRunner
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .run(context -> assertThat(context).hasSingleBean(InboxStorage.class));
    }

    @Test
    void givenDomainStoreMissingAndMongoTemplateWithDataSource_whenContextLoads_thenSkipInboxStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> assertThat(context).doesNotHaveBean(InboxStorage.class));
    }

    @Test
    void givenDomainStorePostgresAndMongoTemplate_whenContextLoads_thenSkipInboxStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.domain-store=POSTGRES")
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .run(context -> assertThat(context).doesNotHaveBean(InboxStorage.class));
    }

    @Test
    void givenEnableInboxAndMongoStoreAndMongoTemplate_whenContextLoads_thenCreateForgeInboxAndStorage() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();
        final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(EnableInboxTestConfiguration.class);

        //when
        //then
        runner.withPropertyValues(
                        "forge.inbox.domain-store=MONGO",
                        "forge.inbox.worker.enabled=false")
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(InboxStorage.class);
                    assertThat(context).hasSingleBean(ForgeInbox.class);
                });
    }

    @Test
    void givenEnableInboxAndDomainStoreNoneAndMongoAndDataSource_whenContextLoads_thenFailFastOnAmbiguousAutoDetection() {
        //given
        final MongoTemplate mongoTemplate = this.getMongoTemplate();
        final DataSource dataSource = mock(DataSource.class);
        final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(EnableInboxTestConfiguration.class);

        //when
        //then
        runner.withPropertyValues("forge.inbox.domain-store=NONE")
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .withBean(DataSource.class, () -> dataSource)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("auto-detection is ambiguous")
                            .hasMessageContaining("domain-store=NONE");
                });
    }

    private MongoTemplate getMongoTemplate() {
        final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        final IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(MongoInboxStorage.COLLECTION_NAME)).thenReturn(indexOperations);
        when(indexOperations.ensureIndex(any())).thenReturn("index");
        return mongoTemplate;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableInbox
    private static class EnableInboxTestConfiguration {
    }
}
