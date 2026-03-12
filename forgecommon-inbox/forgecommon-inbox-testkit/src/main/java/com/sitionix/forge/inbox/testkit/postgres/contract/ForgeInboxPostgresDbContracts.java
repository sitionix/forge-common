package com.sitionix.forge.inbox.testkit.postgres.contract;

import com.sitionix.forge.inbox.postgres.entity.ForgeInboxEventEntity;
import com.sitionix.forgeit.core.contract.ForgeDbContracts;
import com.sitionix.forgeit.domain.contract.DbContract;
import com.sitionix.forgeit.domain.contract.DbContractsDsl;
import com.sitionix.forgeit.domain.contract.clean.CleanupPolicy;

@ForgeDbContracts
public final class ForgeInboxPostgresDbContracts {

    private ForgeInboxPostgresDbContracts() {
    }

    public static final DbContract<ForgeInboxEventEntity> FORGE_INBOX_EVENT_ENTITY_DB_CONTRACT =
            DbContractsDsl.entity(ForgeInboxEventEntity.class)
                    .cleanupPolicy(CleanupPolicy.DELETE_ALL)
                    .build();
}
