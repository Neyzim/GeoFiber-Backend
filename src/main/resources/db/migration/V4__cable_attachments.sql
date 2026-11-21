-- Elementos (caixas ou OLT) por onde um cabo passa, na ordem do traçado.
-- É a base para o backend recusar ligações de fibras em elementos onde o cabo não está.
CREATE TABLE cable_attachment (
    id UUID PRIMARY KEY,
    cable_id UUID NOT NULL REFERENCES network_cable(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    role VARCHAR(16) NOT NULL CHECK (role IN ('START', 'PASS_THROUGH', 'END')),
    box_id UUID REFERENCES network_box(id),
    olt_id UUID REFERENCES network_olt(id),
    CONSTRAINT ck_cable_attachment_single_element CHECK ((box_id IS NULL) <> (olt_id IS NULL)),
    CONSTRAINT uq_cable_attachment_box UNIQUE (cable_id, box_id),
    CONSTRAINT uq_cable_attachment_olt UNIQUE (cable_id, olt_id),
    CONSTRAINT uq_cable_attachment_sequence UNIQUE (cable_id, sequence_number)
);
CREATE INDEX ix_cable_attachment_box ON cable_attachment (box_id);
CREATE INDEX ix_cable_attachment_olt ON cable_attachment (olt_id);
