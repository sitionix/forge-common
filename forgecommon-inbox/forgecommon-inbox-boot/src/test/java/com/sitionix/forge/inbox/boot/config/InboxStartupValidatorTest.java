package com.sitionix.forge.inbox.boot.config;

import com.sitionix.forge.inbox.core.model.InboxDomainStore;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.port.InboxStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InboxStartupValidatorTest {

    @Test
    void givenDomainStoreNoneAndStorageMissing_whenValidatorRuns_thenPass() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(InboxDomainStore.NONE);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        final InboxStartupValidator validator = new InboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(InboxStorage.class),
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(ForgeInboxEventTypes.class),
                beanFactory);

        //when
        //then
        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void givenExplicitDomainStoreAndStorageMissing_whenValidatorRuns_thenFailFast() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(InboxDomainStore.POSTGRES);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();

        final InboxStartupValidator validator = new InboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(InboxStorage.class),
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(ForgeInboxEventTypes.class),
                beanFactory);

        //when
        //then
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain-store=POSTGRES");
    }

    @Test
    void givenStorageAndEventTypesPresent_whenValidatorRuns_thenPass() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(InboxDomainStore.POSTGRES);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("inboxStorage", mock(InboxStorage.class));
        beanFactory.addBean("eventTypes", mock(ForgeInboxEventTypes.class));

        final InboxStartupValidator validator = new InboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(InboxStorage.class),
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(ForgeInboxEventTypes.class),
                beanFactory);

        //when
        //then
        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void givenStoragePresentAndEventTypesMissing_whenValidatorRuns_thenFailFast() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(InboxDomainStore.POSTGRES);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("inboxStorage", mock(InboxStorage.class));

        final InboxStartupValidator validator = new InboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(InboxStorage.class),
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(ForgeInboxEventTypes.class),
                beanFactory);

        //when
        //then
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no ForgeInboxEventTypes bean is configured");
    }

    @Test
    void givenDomainStoreNoneAndDataSourcePresentAndStorageMissing_whenValidatorRuns_thenFailFast() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.setDomainStore(InboxDomainStore.NONE);

        final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("dataSource", mock(DataSource.class));

        final InboxStartupValidator validator = new InboxStartupValidator(
                properties,
                beanFactory.getBeanProvider(InboxStorage.class),
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(ForgeInboxEventTypes.class),
                beanFactory);

        //when
        //then
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain-store=NONE")
                .hasMessageContaining("Set forge.inbox.domain-store explicitly");
    }
}
