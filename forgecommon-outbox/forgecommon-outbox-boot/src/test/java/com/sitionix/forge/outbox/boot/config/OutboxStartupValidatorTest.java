package com.sitionix.forge.outbox.boot.config;

import com.sitionix.forge.outbox.core.model.OutboxDomainStore;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OutboxStartupValidatorTest {

    @Test
    void givenDomainStoreNoneAndStorageMissing_whenValidatorRuns_thenPass() {
        //given
        final ForgeOutboxProperties properties = new ForgeOutboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(OutboxDomainStore.NONE);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        final OutboxStartupValidator validator = new OutboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(OutboxStorage.class));

        //when
        //then
        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void givenExplicitDomainStoreAndStorageMissing_whenValidatorRuns_thenFailFast() {
        //given
        final ForgeOutboxProperties properties = new ForgeOutboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(OutboxDomainStore.POSTGRES);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();

        final OutboxStartupValidator validator = new OutboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(OutboxStorage.class));

        //when
        //then
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain-store=POSTGRES");
    }

    @Test
    void givenStoragePresent_whenValidatorRuns_thenPass() {
        //given
        final ForgeOutboxProperties properties = new ForgeOutboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(OutboxDomainStore.POSTGRES);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("outboxStorage", mock(OutboxStorage.class));

        final OutboxStartupValidator validator = new OutboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(OutboxStorage.class));

        //when
        //then
        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }
}
