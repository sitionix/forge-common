ALTER TABLE forge_inbox_aggregate_types
    ALTER COLUMN id DROP DEFAULT;

DROP SEQUENCE IF EXISTS forge_inbox_aggregate_types_id_seq;
