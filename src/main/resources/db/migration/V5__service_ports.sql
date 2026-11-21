-- Portas de atendimento da CTO: onde o drop do cliente é conectado. Cada porta é alimentada
-- por uma saída de splitter da mesma caixa ou por uma ponta de fibra (nunca as duas).
CREATE TABLE service_port (
    id UUID PRIMARY KEY,
    box_id UUID NOT NULL REFERENCES network_box(id),
    port_number INTEGER NOT NULL CHECK (port_number > 0),
    label VARCHAR(80),
    status VARCHAR(16) NOT NULL,
    splitter_port_id UUID UNIQUE REFERENCES splitter_port(id),
    CONSTRAINT uq_service_port_box_number UNIQUE (box_id, port_number)
);

ALTER TABLE fiber_termination
    ADD COLUMN service_port_id UUID UNIQUE REFERENCES service_port(id);
ALTER TABLE fiber_termination DROP CONSTRAINT ck_termination_single_port;
ALTER TABLE fiber_termination ADD CONSTRAINT ck_termination_single_port
    CHECK (num_nonnulls(olt_port_id, splitter_port_id, service_port_id) <= 1);

-- Clientes passam a ser atendidos por porta de atendimento. Cada cliente já ligado a uma
-- saída de splitter ganha uma porta na caixa do splitter, alimentada por essa saída.
ALTER TABLE customer ADD COLUMN service_port_id UUID UNIQUE REFERENCES service_port(id);

INSERT INTO service_port (id, box_id, port_number, status, splitter_port_id)
SELECT gen_random_uuid(), s.box_id,
       ROW_NUMBER() OVER (PARTITION BY s.box_id ORDER BY s.id, sp.port_number)::INTEGER,
       'OCCUPIED', sp.id
FROM customer c
JOIN splitter_port sp ON sp.id = c.splitter_port_id
JOIN splitter s ON s.id = sp.splitter_id;

UPDATE customer
SET service_port_id = sp.id
FROM service_port sp
WHERE sp.splitter_port_id = customer.splitter_port_id;

ALTER TABLE customer DROP COLUMN splitter_port_id;

-- Uma porta de atendimento tem no máximo uma fonte óptica, e uma saída de splitter alimenta
-- uma porta de atendimento ou uma fibra, não as duas.
CREATE FUNCTION assert_single_optical_source() RETURNS trigger AS $$
BEGIN
    IF TG_TABLE_NAME = 'service_port' THEN
        IF NEW.splitter_port_id IS NOT NULL AND (
            EXISTS (SELECT 1 FROM fiber_termination WHERE service_port_id = NEW.id)
            OR EXISTS (SELECT 1 FROM fiber_termination WHERE splitter_port_id = NEW.splitter_port_id)
        ) THEN
            RAISE EXCEPTION 'Service port % would have more than one optical source', NEW.id
                USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.service_port_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM service_port WHERE id = NEW.service_port_id AND splitter_port_id IS NOT NULL
        ) THEN
            RAISE EXCEPTION 'Service port % is already fed by a splitter', NEW.service_port_id
                USING ERRCODE = '23514';
        END IF;
        IF NEW.splitter_port_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM service_port WHERE splitter_port_id = NEW.splitter_port_id
        ) THEN
            RAISE EXCEPTION 'Splitter port % already feeds a service port', NEW.splitter_port_id
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_service_port_single_source
BEFORE INSERT OR UPDATE OF splitter_port_id ON service_port
FOR EACH ROW EXECUTE FUNCTION assert_single_optical_source();

CREATE TRIGGER trg_termination_single_source
BEFORE INSERT OR UPDATE OF service_port_id, splitter_port_id ON fiber_termination
FOR EACH ROW EXECUTE FUNCTION assert_single_optical_source();
