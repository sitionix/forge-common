package com.sitionix.forge.inbox.postgres.it.infra;

import com.sitionix.forgeit.core.annotation.ForgeFeatures;
import com.sitionix.forgeit.core.api.ForgeIT;
import com.sitionix.forgeit.postgresql.api.PostgresqlSupport;

@ForgeFeatures(value = {
        PostgresqlSupport.class
})
public interface TestManager extends ForgeIT {
}
