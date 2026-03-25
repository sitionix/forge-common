ALTER TABLE forge_outbox_events
    ADD COLUMN IF NOT EXISTS idempotency_id UUID,
    ADD COLUMN IF NOT EXISTS headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS initiator_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS initiator_id VARCHAR(255);

UPDATE forge_outbox_events
SET idempotency_id = (
    LOWER(
        SUBSTRING(md5(CONCAT_WS('|', COALESCE(event_type, ''), id::text, COALESCE(created_at::text, ''))) FROM 1 FOR 8) || '-' ||
        SUBSTRING(md5(CONCAT_WS('|', COALESCE(event_type, ''), id::text, COALESCE(created_at::text, ''))) FROM 9 FOR 4) || '-' ||
        SUBSTRING(md5(CONCAT_WS('|', COALESCE(event_type, ''), id::text, COALESCE(created_at::text, ''))) FROM 13 FOR 4) || '-' ||
        SUBSTRING(md5(CONCAT_WS('|', COALESCE(event_type, ''), id::text, COALESCE(created_at::text, ''))) FROM 17 FOR 4) || '-' ||
        SUBSTRING(md5(CONCAT_WS('|', COALESCE(event_type, ''), id::text, COALESCE(created_at::text, ''))) FROM 21 FOR 12)
    )::uuid
)
WHERE idempotency_id IS NULL;

ALTER TABLE forge_outbox_events
    ALTER COLUMN idempotency_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_forge_outbox_events_idempotency_id
    ON forge_outbox_events (idempotency_id);
