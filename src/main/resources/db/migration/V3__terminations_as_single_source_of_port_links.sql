-- A V2 passou a registrar a ligação fibra <-> porta em fiber_termination (olt_port_id /
-- splitter_port_id) e já copiou os dados de olt_port.fiber_id e splitter_port.fiber_id.
-- Manter as duas colunas permitiria que as duas fontes divergissem, então as antigas saem.
ALTER TABLE olt_port DROP COLUMN fiber_id;
ALTER TABLE splitter_port DROP COLUMN fiber_id;

-- Consultas por caixa (splitters e emendas) não tinham índice na chave estrangeira.
CREATE INDEX ix_splitter_box ON splitter (box_id);
CREATE INDEX ix_splice_box ON splice (box_id);
