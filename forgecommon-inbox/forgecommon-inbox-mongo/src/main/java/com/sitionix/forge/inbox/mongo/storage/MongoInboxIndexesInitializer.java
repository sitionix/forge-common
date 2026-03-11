package com.sitionix.forge.inbox.mongo.storage;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public class MongoInboxIndexesInitializer implements InitializingBean {

    private final MongoTemplate mongoTemplate;

    public MongoInboxIndexesInitializer(final MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        this.mongoTemplate.indexOps(MongoInboxStorage.COLLECTION_NAME)
                .ensureIndex(new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("nextAttemptAt", Sort.Direction.ASC));
        this.mongoTemplate.indexOps(MongoInboxStorage.COLLECTION_NAME)
                .ensureIndex(new Index().on("eventType", Sort.Direction.ASC));
        this.mongoTemplate.indexOps(MongoInboxStorage.COLLECTION_NAME)
                .ensureIndex(new Index().on("lockUntil", Sort.Direction.ASC));
        this.mongoTemplate.indexOps(MongoInboxStorage.COLLECTION_NAME)
                .ensureIndex(new Index().on("idempotencyKey", Sort.Direction.ASC).unique());
    }
}
