CREATE TABLE IF NOT EXISTS forge_inbox_statuses (
    id          BIGINT PRIMARY KEY,
    description VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS forge_inbox_aggregate_types (
    id          BIGINT PRIMARY KEY,
    description VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS forge_inbox_events (
    id                BIGSERIAL PRIMARY KEY,
    event_type        VARCHAR(255) NOT NULL,
    payload           TEXT         NOT NULL,
    headers           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metadata          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    trace_id          VARCHAR(255),
    idempotency_key   VARCHAR(255) NOT NULL,
    aggregate_type_id BIGINT,
    aggregate_id      BIGINT,
    initiator_type    VARCHAR(255),
    initiator_id      VARCHAR(255),
    status_id         BIGINT       NOT NULL,
    retry_count       INT          NOT NULL,
    next_retry_at     TIMESTAMPTZ  NOT NULL,
    last_error        TEXT,
    lock_until        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_forge_inbox_events_aggregate_type_id
        FOREIGN KEY (aggregate_type_id) REFERENCES forge_inbox_aggregate_types (id),
    CONSTRAINT fk_forge_inbox_events_status_id
        FOREIGN KEY (status_id) REFERENCES forge_inbox_statuses (id)
);

CREATE INDEX IF NOT EXISTS idx_forge_inbox_events_status_id
    ON forge_inbox_events (status_id);

CREATE INDEX IF NOT EXISTS idx_forge_inbox_events_aggregate
    ON forge_inbox_events (aggregate_type_id, aggregate_id);

CREATE INDEX IF NOT EXISTS idx_forge_inbox_events_event_type
    ON forge_inbox_events (event_type);

CREATE INDEX IF NOT EXISTS idx_forge_inbox_events_polling
    ON forge_inbox_events (status_id, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_forge_inbox_events_lock_until
    ON forge_inbox_events (lock_until);

CREATE UNIQUE INDEX IF NOT EXISTS uq_forge_inbox_events_idempotency_key
    ON forge_inbox_events (idempotency_key);
