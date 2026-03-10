package com.sitionix.forge.inbox.postgres.config;

import com.sitionix.forge.inbox.core.port.InboxStorage;
import com.sitionix.forge.inbox.postgres.entity.ForgeInboxEventEntity;
import com.sitionix.forge.inbox.postgres.storage.PostgresInboxStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class ForgeInboxPostgresAutoConfiguration {

    @Bean
    public static BeanFactoryPostProcessor forgeInboxEntityScanPackagesConfigurer() {
        return beanFactory -> {
            if (!(beanFactory instanceof final BeanDefinitionRegistry registry)) {
                return;
            }

            final List<String> packageNames = new ArrayList<>();
            if (AutoConfigurationPackages.has(beanFactory)) {
                packageNames.addAll(AutoConfigurationPackages.get(beanFactory));
            }
            packageNames.add(ForgeInboxEventEntity.class.getPackageName());

            EntityScanPackages.register(registry, packageNames);
        };
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public NamedParameterJdbcTemplate forgeInboxNamedParameterJdbcTemplate(final DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "forge.inbox", name = "domain-store", havingValue = "POSTGRES")
    @ConditionalOnMissingBean(InboxStorage.class)
    static class ExplicitPostgresInboxStorageConfiguration {

        @Bean
        public InboxStorage postgresInboxStorage(final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
            return new PostgresInboxStorage(namedParameterJdbcTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "forge.inbox", name = "domain-store", havingValue = "NONE", matchIfMissing = true)
    @ConditionalOnMissingBean(value = InboxStorage.class, type = "org.springframework.data.mongodb.core.MongoTemplate")
    static class AutoDetectedPostgresInboxStorageConfiguration {

        @Bean
        public InboxStorage postgresInboxStorageByAutoDetection(
                final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
            return new PostgresInboxStorage(namedParameterJdbcTemplate);
        }
    }
}
