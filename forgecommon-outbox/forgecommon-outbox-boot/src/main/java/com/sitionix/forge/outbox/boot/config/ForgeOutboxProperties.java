package com.sitionix.forge.outbox.boot.config;

import com.sitionix.forge.outbox.core.model.OutboxDomainStore;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "forge.outbox")
public class ForgeOutboxProperties {

    private boolean enabled = true;

    private OutboxDomainStore domainStore = OutboxDomainStore.NONE;

    private final Worker worker = new Worker();

    private final Cleanup cleanup = new Cleanup();

    @Data
    public static class Worker {

        private boolean enabled = true;

        private Duration fixedDelay = Duration.ofSeconds(5);

        @Min(1)
        private int batchSize = 50;

        private Duration retryDelay = Duration.ofSeconds(60);

        @Min(1)
        private int maxRetries = 5;

        private final Lock lock = new Lock();
    }

    @Data
    public static class Lock {

        private boolean enabled = true;

        private Duration lease = Duration.ofSeconds(30);
    }

    @Data
    public static class Cleanup {

        private boolean enabled = true;

        private Duration fixedDelay = Duration.ofHours(1);

        private Duration retention = Duration.ofDays(14);
    }
}
