CREATE SEQUENCE IF NOT EXISTS forge_inbox_aggregate_types_id_seq;

SELECT setval(
        'forge_inbox_aggregate_types_id_seq',
        COALESCE((SELECT MAX(id) FROM forge_inbox_aggregate_types), 0) + 1,
        false
       );

ALTER TABLE forge_inbox_aggregate_types
    ALTER COLUMN id SET DEFAULT nextval('forge_inbox_aggregate_types_id_seq');

ALTER SEQUENCE forge_inbox_aggregate_types_id_seq
    OWNED BY forge_inbox_aggregate_types.id;
