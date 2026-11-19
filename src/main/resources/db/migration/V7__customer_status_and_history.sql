-- Situação do contrato do cliente. Clientes cancelados não são apagados: ficam como histórico.
ALTER TABLE customer ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE customer ADD CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CANCELLED'));
ALTER TABLE customer ALTER COLUMN status DROP DEFAULT;

-- Uma linha por mudança de situação ou de porta, com a porta que atendia o cliente naquele momento.
CREATE TABLE customer_status_history (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CANCELLED')),
    service_port_id UUID REFERENCES service_port(id) ON DELETE SET NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_customer_status_history_customer ON customer_status_history (customer_id, changed_at);

INSERT INTO customer_status_history (id, customer_id, status, service_port_id, changed_at)
SELECT gen_random_uuid(), id, status, service_port_id, now() FROM customer;
