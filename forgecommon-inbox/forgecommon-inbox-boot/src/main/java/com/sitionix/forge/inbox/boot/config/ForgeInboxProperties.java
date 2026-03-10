package com.sitionix.forge.inbox.boot.config;

import com.sitionix.forge.inbox.core.model.InboxDomainStore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.time.DurationMin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "forge.inbox")
public class ForgeInboxProperties {

    private boolean enabled = true;

    @NotNull
    private InboxDomainStore domainStore = InboxDomainStore.NONE;

    private final Worker worker = new Worker();

    private final Cleanup cleanup = new Cleanup();

    @Data
    public static class Worker {

        private boolean enabled = true;

        @NotNull
        @DurationMin(millis = 1)
        private Duration fixedDelay = Duration.ofSeconds(5);

        @Min(1)
        private int batchSize = 50;

        @NotNull
        @DurationMin(millis = 0)
        private Duration retryDelay = Duration.ofSeconds(60);

        @Min(1)
        private int maxRetries = 5;

        private final Lock lock = new Lock();
    }

    @Data
    public static class Lock {

        private boolean enabled = true;

        @NotNull
        @DurationMin(millis = 1)
        private Duration lease = Duration.ofSeconds(30);
    }

    @Data
    public static class Cleanup {

        private boolean enabled = true;

        @NotNull
        @DurationMin(millis = 1)
        private Duration fixedDelay = Duration.ofHours(1);

        @NotNull
        @DurationMin(millis = 1)
        private Duration retention = Duration.ofDays(14);
    }
}
