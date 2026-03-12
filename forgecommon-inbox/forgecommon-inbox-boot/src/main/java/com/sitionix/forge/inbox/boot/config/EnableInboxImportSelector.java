package com.sitionix.forge.inbox.boot.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.Arrays;

/**
 * Selects inbox auto-configurations available on the classpath.
 */
public class EnableInboxImportSelector implements DeferredImportSelector {

    private static final String[] CANDIDATE_CONFIGURATIONS = {
            "com.sitionix.forge.inbox.postgres.config.ForgeInboxPostgresAutoConfiguration",
            "com.sitionix.forge.inbox.mongo.config.ForgeInboxMongoAutoConfiguration",
            "com.sitionix.forge.inbox.boot.config.ForgeInboxAutoConfiguration"
    };

    @Override
    public String[] selectImports(final AnnotationMetadata importingClassMetadata) {
        final ClassLoader classLoader = EnableInboxImportSelector.class.getClassLoader();
        return Arrays.stream(CANDIDATE_CONFIGURATIONS)
                .filter(configurationClass -> ClassUtils.isPresent(configurationClass, classLoader))
                .toArray(String[]::new);
    }
}
