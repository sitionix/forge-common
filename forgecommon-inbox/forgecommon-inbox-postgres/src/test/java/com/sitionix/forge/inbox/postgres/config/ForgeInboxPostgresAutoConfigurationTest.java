package com.sitionix.forge.inbox.postgres.config;

import com.sitionix.forge.inbox.core.port.InboxStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ForgeInboxPostgresAutoConfigurationTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ForgeInboxPostgresAutoConfiguration.class));
    }

    @Test
    void givenDomainStorePostgresAndDataSource_whenContextLoads_thenCreateInboxStorage() {
        //given
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.domain-store=POSTGRES")
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                    assertThat(context).hasSingleBean(InboxStorage.class);
                });
    }

    @Test
    void givenDomainStoreMissingAndDataSource_whenContextLoads_thenCreateInboxStorageByAutoDetection() {
        //given
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                    assertThat(context).hasSingleBean(InboxStorage.class);
                });
    }

    @Test
    void givenDomainStoreMongoAndDataSource_whenContextLoads_thenSkipInboxStorage() {
        //given
        final DataSource dataSource = mock(DataSource.class);

        //when
        //then
        this.contextRunner
                .withPropertyValues("forge.inbox.domain-store=MONGO")
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                    assertThat(context).doesNotHaveBean(InboxStorage.class);
                });
    }
}
