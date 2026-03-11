ALTER TABLE forge_inbox_events
    ADD COLUMN IF NOT EXISTS headers JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE forge_inbox_events
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE forge_inbox_events
    ADD COLUMN IF NOT EXISTS initiator_type VARCHAR(255);

ALTER TABLE forge_inbox_events
    ADD COLUMN IF NOT EXISTS initiator_id VARCHAR(255);

UPDATE forge_inbox_events
SET idempotency_key = CONCAT('legacy-', id)
WHERE idempotency_key IS NULL
   OR BTRIM(idempotency_key) = '';

ALTER TABLE forge_inbox_events
    ALTER COLUMN idempotency_key SET NOT NULL;
