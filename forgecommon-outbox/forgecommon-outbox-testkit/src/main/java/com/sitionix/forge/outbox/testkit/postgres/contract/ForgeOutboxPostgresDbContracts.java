package com.sitionix.forge.outbox.testkit.postgres.contract;

import com.sitionix.forge.outbox.postgres.entity.ForgeOutboxEventEntity;
import com.sitionix.forgeit.core.contract.ForgeDbContracts;
import com.sitionix.forgeit.domain.contract.DbContract;
import com.sitionix.forgeit.domain.contract.DbContractsDsl;
import com.sitionix.forgeit.domain.contract.clean.CleanupPolicy;

@ForgeDbContracts
public final class ForgeOutboxPostgresDbContracts {

    private ForgeOutboxPostgresDbContracts() {
    }

    public static final DbContract<ForgeOutboxEventEntity> FORGE_OUTBOX_EVENT_ENTITY_DB_CONTRACT =
            DbContractsDsl.entity(ForgeOutboxEventEntity.class)
                    .cleanupPolicy(CleanupPolicy.DELETE_ALL)
                    .build();
}
