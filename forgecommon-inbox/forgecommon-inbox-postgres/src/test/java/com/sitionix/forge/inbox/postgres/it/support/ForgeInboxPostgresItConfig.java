package com.sitionix.forge.inbox.postgres.it.support;

import com.sitionix.forge.inbox.core.model.EnumForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ForgeInboxPostgresItConfig {

    @Bean
    ForgeInboxPostgresPublishedEvents publishedEvents() {
        return new ForgeInboxPostgresPublishedEvents();
    }

    @Bean
    ForgeInboxEventHandler<?> successPublisher(final ForgeInboxPostgresPublishedEvents publishedEvents) {
        return new SuccessInboxEventHandler(publishedEvents);
    }

    @Bean
    ForgeInboxEventHandler<?> failingPublisher() {
        return new FailingInboxEventHandler();
    }

    @Bean
    ForgeInboxEventTypes eventTypes() {
        return new EnumForgeInboxEventTypes<>(TestInboxEventType.class);
    }
}
