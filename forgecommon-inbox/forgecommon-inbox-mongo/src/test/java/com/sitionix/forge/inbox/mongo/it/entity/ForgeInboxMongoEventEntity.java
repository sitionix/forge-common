package com.sitionix.forge.inbox.mongo.it.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document("forge_inbox_events")
public class ForgeInboxMongoEventEntity {

    @Id
    private String id;
    private String eventType;
    private String payload;
    private Map<String, String> headers;
    private Map<String, String> metadata;
    private String traceId;
    private String idempotencyKey;
    private String aggregateType;
    private Long aggregateId;
    private String initiatorType;
    private String initiatorId;
    private String status;
    private Integer attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant lockUntil;
    private Instant createdAt;
    private Instant updatedAt;
}
