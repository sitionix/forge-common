CREATE SEQUENCE IF NOT EXISTS forge_outbox_aggregate_types_id_seq;

SELECT setval(
        'forge_outbox_aggregate_types_id_seq',
        COALESCE((SELECT MAX(id) FROM forge_outbox_aggregate_types), 0) + 1,
        false
       );

ALTER TABLE forge_outbox_aggregate_types
    ALTER COLUMN id SET DEFAULT nextval('forge_outbox_aggregate_types_id_seq');

ALTER SEQUENCE forge_outbox_aggregate_types_id_seq
    OWNED BY forge_outbox_aggregate_types.id;
