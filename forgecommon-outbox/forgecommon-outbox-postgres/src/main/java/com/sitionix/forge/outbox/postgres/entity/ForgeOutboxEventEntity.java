package com.sitionix.forge.outbox.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "forge_outbox_events")
public class ForgeOutboxEventEntity {

    @Id
    private Long id;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "payload")
    private String payload;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "aggregate_type_id")
    private Long aggregateTypeId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "lock_until")
    private Instant lockUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getEventType() {
        return this.eventType;
    }

    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return this.payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }

    public String getTraceId() {
        return this.traceId;
    }

    public void setTraceId(final String traceId) {
        this.traceId = traceId;
    }

    public Long getAggregateTypeId() {
        return this.aggregateTypeId;
    }

    public void setAggregateTypeId(final Long aggregateTypeId) {
        this.aggregateTypeId = aggregateTypeId;
    }

    public Long getAggregateId() {
        return this.aggregateId;
    }

    public void setAggregateId(final Long aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Long getStatusId() {
        return this.statusId;
    }

    public void setStatusId(final Long statusId) {
        this.statusId = statusId;
    }

    public Integer getRetryCount() {
        return this.retryCount;
    }

    public void setRetryCount(final Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRetryAt() {
        return this.nextRetryAt;
    }

    public void setNextRetryAt(final Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return this.lastError;
    }

    public void setLastError(final String lastError) {
        this.lastError = lastError;
    }

    public Instant getLockUntil() {
        return this.lockUntil;
    }

    public void setLockUntil(final Instant lockUntil) {
        this.lockUntil = lockUntil;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
