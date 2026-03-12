package com.sitionix.forge.inbox.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "forge_inbox_events")
public class ForgeInboxEventEntity {

    @Id
    private Long id;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "headers", columnDefinition = "jsonb")
    private String headers;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "aggregate_type_id")
    private Long aggregateTypeId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "initiator_type")
    private String initiatorType;

    @Column(name = "initiator_id")
    private String initiatorId;

    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "lock_until")
    private Instant lockUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
