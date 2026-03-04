package com.sitionix.forge.outbox.postgres.config;

import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ForgeOutboxPostgresAutoConfigurationTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ForgeOutboxPostgresAutoConfiguration.class));
    }

    @Test
    void givenDomainStorePostgresAndDataSource_whenContextLoads_thenCreateOutboxStorage() {
        //given
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.outbox.domain-store=POSTGRES")
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                    assertThat(context).hasSingleBean(OutboxStorage.class);
                });
    }

    @Test
    void givenDomainStoreMissingAndDataSource_whenContextLoads_thenCreateOutboxStorageByAutoDetection() {
        //given
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                    assertThat(context).hasSingleBean(OutboxStorage.class);
                });
    }

    @Test
    void givenDomainStoreMongoAndDataSource_whenContextLoads_thenSkipOutboxStorage() {
        //given
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.outbox.domain-store=MONGO")
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                    assertThat(context).doesNotHaveBean(OutboxStorage.class);
                });
    }
}
