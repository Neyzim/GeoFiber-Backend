CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE network_box (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    usage_status VARCHAR(20) NOT NULL,
    box_type VARCHAR(16) NOT NULL,
    power_capacity INTEGER CHECK (power_capacity IS NULL OR power_capacity > 0),
    tray_capacity INTEGER CHECK (tray_capacity IS NULL OR tray_capacity > 0),
    manufacturer VARCHAR(120),
    model VARCHAR(120),
    address VARCHAR(500),
    geom geometry(Point, 4326) NOT NULL
);
CREATE INDEX ix_network_box_geom ON network_box USING GIST (geom);

CREATE TABLE network_cable (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    usage_status VARCHAR(20) NOT NULL,
    cable_role VARCHAR(24) NOT NULL,
    manufacturer VARCHAR(120),
    model VARCHAR(120),
    fiber_capacity INTEGER NOT NULL CHECK (fiber_capacity > 0),
    length_meters DOUBLE PRECISION NOT NULL DEFAULT 0,
    geom geometry(LineString, 4326) NOT NULL
);
CREATE INDEX ix_network_cable_geom ON network_cable USING GIST (geom);

CREATE FUNCTION update_cable_length_meters() RETURNS trigger AS $$
BEGIN
    NEW.length_meters := ST_Length(NEW.geom::geography);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_network_cable_length
BEFORE INSERT OR UPDATE OF geom ON network_cable
FOR EACH ROW EXECUTE FUNCTION update_cable_length_meters();

CREATE TABLE loose_tube (
    id UUID PRIMARY KEY,
    cable_id UUID NOT NULL REFERENCES network_cable(id),
    tube_number INTEGER NOT NULL CHECK (tube_number > 0),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    color VARCHAR(16) NOT NULL,
    CONSTRAINT uq_loose_tube_number UNIQUE (cable_id, tube_number)
);

CREATE TABLE fiber (
    id UUID PRIMARY KEY,
    cable_id UUID NOT NULL REFERENCES network_cable(id),
    tube_id UUID NOT NULL REFERENCES loose_tube(id),
    number_in_tube INTEGER NOT NULL CHECK (number_in_tube > 0),
    absolute_number INTEGER NOT NULL CHECK (absolute_number > 0),
    color VARCHAR(16) NOT NULL,
    CONSTRAINT uq_fiber_absolute_number UNIQUE (cable_id, absolute_number),
    CONSTRAINT uq_fiber_tube_number UNIQUE (tube_id, number_in_tube)
);

CREATE TABLE network_olt (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    usage_status VARCHAR(20) NOT NULL,
    model VARCHAR(120),
    geom geometry(Point, 4326) NOT NULL
);
CREATE INDEX ix_network_olt_geom ON network_olt USING GIST (geom);

CREATE TABLE olt_port (
    id UUID PRIMARY KEY,
    olt_id UUID NOT NULL REFERENCES network_olt(id),
    slot_number INTEGER NOT NULL CHECK (slot_number > 0),
    port_number INTEGER NOT NULL CHECK (port_number > 0),
    status VARCHAR(16) NOT NULL,
    fiber_id UUID UNIQUE REFERENCES fiber(id),
    CONSTRAINT uq_olt_port_position UNIQUE (olt_id, slot_number, port_number)
);

CREATE TABLE splitter (
    id UUID PRIMARY KEY,
    ratio VARCHAR(16) NOT NULL,
    balanced BOOLEAN NOT NULL,
    box_id UUID NOT NULL REFERENCES network_box(id)
);

CREATE TABLE splitter_port (
    id UUID PRIMARY KEY,
    splitter_id UUID NOT NULL REFERENCES splitter(id),
    port_number INTEGER NOT NULL CHECK (port_number > 0),
    attenuation_db DOUBLE PRECISION CHECK (attenuation_db IS NULL OR attenuation_db >= 0),
    status VARCHAR(16) NOT NULL,
    port_direction VARCHAR(8) NOT NULL,
    fiber_id UUID UNIQUE REFERENCES fiber(id),
    CONSTRAINT uq_splitter_port_number UNIQUE (splitter_id, port_number)
);

CREATE TABLE customer (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    contract_code VARCHAR(80) NOT NULL UNIQUE,
    address VARCHAR(500),
    geom geometry(Point, 4326) NOT NULL,
    splitter_port_id UUID UNIQUE REFERENCES splitter_port(id)
);
CREATE INDEX ix_customer_geom ON customer USING GIST (geom);

CREATE TABLE splice (
    id UUID PRIMARY KEY,
    attenuation_db DOUBLE PRECISION CHECK (attenuation_db IS NULL OR attenuation_db >= 0),
    tray_number INTEGER CHECK (tray_number IS NULL OR tray_number > 0),
    box_id UUID NOT NULL REFERENCES network_box(id),
    input_fiber_id UUID NOT NULL UNIQUE REFERENCES fiber(id),
    output_fiber_id UUID NOT NULL UNIQUE REFERENCES fiber(id),
    CONSTRAINT ck_splice_distinct_fibers CHECK (input_fiber_id <> output_fiber_id)
);
