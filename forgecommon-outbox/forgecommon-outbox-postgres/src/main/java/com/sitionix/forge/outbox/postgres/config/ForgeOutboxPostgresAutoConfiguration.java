package com.sitionix.forge.outbox.postgres.config;

import com.sitionix.forge.outbox.core.port.OutboxStorage;
import com.sitionix.forge.outbox.postgres.entity.ForgeOutboxEventEntity;
import com.sitionix.forge.outbox.postgres.storage.PostgresOutboxStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
public class ForgeOutboxPostgresAutoConfiguration {

    @Bean
    public static BeanFactoryPostProcessor forgeOutboxEntityScanPackagesConfigurer() {
        return beanFactory -> {
            if (!(beanFactory instanceof final BeanDefinitionRegistry registry)) {
                return;
            }

            final List<String> packageNames = new ArrayList<>();
            if (AutoConfigurationPackages.has(beanFactory)) {
                packageNames.addAll(AutoConfigurationPackages.get(beanFactory));
            }
            packageNames.add(ForgeOutboxEventEntity.class.getPackageName());

            EntityScanPackages.register(registry, packageNames);
        };
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public NamedParameterJdbcTemplate forgeOutboxNamedParameterJdbcTemplate(final DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnExpression("'${forge.outbox.domain-store:NONE}'.equalsIgnoreCase('POSTGRES')")
    @ConditionalOnMissingBean(OutboxStorage.class)
    public OutboxStorage postgresOutboxStorage(final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new PostgresOutboxStorage(namedParameterJdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnExpression("'${forge.outbox.domain-store:NONE}'.equalsIgnoreCase('NONE')")
    @ConditionalOnMissingBean(OutboxStorage.class)
    public OutboxStorage postgresOutboxStorageByAutoDetection(final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new PostgresOutboxStorage(namedParameterJdbcTemplate);
    }
}
