package com.sitionix.forge.inbox.mongo.it.support;

import com.sitionix.forge.inbox.core.model.EnumForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.model.ForgeInboxEventTypes;
import com.sitionix.forge.inbox.core.port.ForgeInboxEventHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ForgeInboxMongoItConfig {

    @Bean
    ForgeInboxMongoPublishedEvents publishedEvents() {
        return new ForgeInboxMongoPublishedEvents();
    }

    @Bean
    ForgeInboxEventHandler<?> successPublisher(final ForgeInboxMongoPublishedEvents publishedEvents) {
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
