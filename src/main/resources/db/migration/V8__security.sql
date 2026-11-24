CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- Criador de cada elemento. Linhas antigas ficam NULL até o usuário inicial ser criado (Task 3).
ALTER TABLE network_box ADD COLUMN created_by UUID REFERENCES app_user(id);
ALTER TABLE network_cable ADD COLUMN created_by UUID REFERENCES app_user(id);
ALTER TABLE network_olt ADD COLUMN created_by UUID REFERENCES app_user(id);
ALTER TABLE customer ADD COLUMN created_by UUID REFERENCES app_user(id);

CREATE INDEX ix_network_box_created_by ON network_box (created_by);
CREATE INDEX ix_network_cable_created_by ON network_cable (created_by);
CREATE INDEX ix_network_olt_created_by ON network_olt (created_by);
CREATE INDEX ix_customer_created_by ON customer (created_by);
