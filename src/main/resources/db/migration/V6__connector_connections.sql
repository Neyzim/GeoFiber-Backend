-- Conexões passam a ter caixa e atenuação, e ganham o tipo CONNECTOR (cordão/adaptador),
-- além da fusão. As fusões existentes herdam caixa e atenuação da emenda.
ALTER TABLE optical_connection
    ADD COLUMN box_id UUID REFERENCES network_box(id),
    ADD COLUMN attenuation_db DOUBLE PRECISION CHECK (attenuation_db IS NULL OR attenuation_db >= 0);

UPDATE optical_connection connection
SET box_id = splice.box_id,
    attenuation_db = splice.attenuation_db
FROM splice
WHERE splice.connection_id = connection.id;

ALTER TABLE optical_connection ALTER COLUMN box_id SET NOT NULL;
CREATE INDEX ix_optical_connection_box ON optical_connection (box_id);

ALTER TABLE optical_connection DROP CONSTRAINT optical_connection_connection_type_check;
ALTER TABLE optical_connection ADD CONSTRAINT ck_optical_connection_type
    CHECK (connection_type IN ('FUSION', 'CONNECTOR'));
