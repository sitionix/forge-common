package com.sitionix.forge.inbox.mongo.it.infra;

import com.sitionix.forgeit.core.annotation.ForgeFeatures;
import com.sitionix.forgeit.core.api.ForgeIT;
import com.sitionix.forgeit.mongodb.api.MongoSupport;

@ForgeFeatures({MongoSupport.class})
public interface TestManager extends ForgeIT {
}
