ALTER TABLE fiber
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'BROKEN', 'RESERVED'));

ALTER TABLE loose_tube
    ADD CONSTRAINT uq_loose_tube_id_cable UNIQUE (id, cable_id);

ALTER TABLE fiber
    ADD CONSTRAINT fk_fiber_tube_belongs_to_cable
    FOREIGN KEY (tube_id, cable_id) REFERENCES loose_tube(id, cable_id);

ALTER TABLE olt_port DROP CONSTRAINT IF EXISTS olt_port_fiber_id_key;
ALTER TABLE splitter_port DROP CONSTRAINT IF EXISTS splitter_port_fiber_id_key;

CREATE TABLE tray (
    id UUID PRIMARY KEY,
    box_id UUID NOT NULL REFERENCES network_box(id),
    tray_number INTEGER NOT NULL CHECK (tray_number > 0),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    CONSTRAINT uq_tray_box_number UNIQUE (box_id, tray_number)
);

CREATE TABLE fiber_termination (
    id UUID PRIMARY KEY,
    fiber_id UUID NOT NULL REFERENCES fiber(id) ON DELETE CASCADE,
    end_code VARCHAR(1) NOT NULL CHECK (end_code IN ('A', 'B')),
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'CONNECTED', 'BROKEN', 'RESERVED')),
    olt_port_id UUID UNIQUE REFERENCES olt_port(id),
    splitter_port_id UUID UNIQUE REFERENCES splitter_port(id),
    CONSTRAINT uq_termination_fiber_end UNIQUE (fiber_id, end_code),
    CONSTRAINT ck_termination_single_port
        CHECK (olt_port_id IS NULL OR splitter_port_id IS NULL)
);

INSERT INTO fiber_termination (id, fiber_id, end_code, status)
SELECT gen_random_uuid(), fiber.id, ends.end_code, 'AVAILABLE'
FROM fiber
CROSS JOIN (VALUES ('A'), ('B')) AS ends(end_code);

UPDATE fiber_termination termination
SET olt_port_id = port.id, status = 'CONNECTED'
FROM olt_port port
WHERE port.fiber_id = termination.fiber_id
  AND termination.end_code = 'A';

UPDATE fiber_termination termination
SET splitter_port_id = port.id, status = 'CONNECTED'
FROM splitter_port port
WHERE port.fiber_id = termination.fiber_id
  AND termination.end_code = 'B';

CREATE TABLE optical_connection (
    id UUID PRIMARY KEY,
    connection_type VARCHAR(16) NOT NULL CHECK (connection_type IN ('FUSION')),
    termination_a_id UUID NOT NULL UNIQUE REFERENCES fiber_termination(id),
    termination_b_id UUID NOT NULL UNIQUE REFERENCES fiber_termination(id),
    CONSTRAINT ck_connection_termination_order CHECK (termination_a_id < termination_b_id)
);

ALTER TABLE splice
    ADD COLUMN connection_id UUID,
    ADD COLUMN tray_id UUID,
    ADD COLUMN position_number INTEGER;

INSERT INTO optical_connection (id, connection_type, termination_a_id, termination_b_id)
SELECT gen_random_uuid(), 'FUSION', LEAST(input_end.id, output_end.id), GREATEST(input_end.id, output_end.id)
FROM splice
JOIN fiber_termination input_end
  ON input_end.fiber_id = splice.input_fiber_id AND input_end.end_code = 'A'
JOIN fiber_termination output_end
  ON output_end.fiber_id = splice.output_fiber_id AND output_end.end_code = 'B';

UPDATE fiber_termination termination
SET status = 'CONNECTED'
WHERE termination.id IN (
    SELECT termination_a_id FROM optical_connection
    UNION
    SELECT termination_b_id FROM optical_connection
);

UPDATE splice
SET connection_id = connection.id
FROM optical_connection connection
JOIN fiber_termination input_end
  ON input_end.id = connection.termination_a_id OR input_end.id = connection.termination_b_id
JOIN fiber_termination output_end
  ON output_end.id = connection.termination_a_id OR output_end.id = connection.termination_b_id
WHERE input_end.fiber_id = splice.input_fiber_id
  AND input_end.end_code = 'A'
  AND output_end.fiber_id = splice.output_fiber_id
  AND output_end.end_code = 'B';

INSERT INTO tray (id, box_id, tray_number, capacity)
SELECT gen_random_uuid(), box_id, COALESCE(tray_number, 1), GREATEST(COUNT(*)::INTEGER, 144)
FROM splice
GROUP BY box_id, COALESCE(tray_number, 1);

WITH numbered_splices AS (
    SELECT splice.id AS splice_id,
           tray.id AS tray_id,
           ROW_NUMBER() OVER (PARTITION BY tray.id ORDER BY splice.id)::INTEGER AS position_number
    FROM splice
    JOIN tray
      ON tray.box_id = splice.box_id
     AND tray.tray_number = COALESCE(splice.tray_number, 1)
)
UPDATE splice
SET tray_id = numbered_splices.tray_id,
    position_number = numbered_splices.position_number
FROM numbered_splices
WHERE splice.id = numbered_splices.splice_id;

ALTER TABLE splice
    ADD CONSTRAINT fk_splice_connection FOREIGN KEY (connection_id) REFERENCES optical_connection(id),
    ADD CONSTRAINT fk_splice_tray FOREIGN KEY (tray_id) REFERENCES tray(id),
    ADD CONSTRAINT ck_splice_position CHECK (position_number > 0),
    ADD CONSTRAINT uq_splice_tray_position UNIQUE (tray_id, position_number),
    ALTER COLUMN connection_id SET NOT NULL,
    ALTER COLUMN tray_id SET NOT NULL,
    ALTER COLUMN position_number SET NOT NULL;

CREATE FUNCTION sync_splice_tray_number() RETURNS trigger AS $$
BEGIN
    SELECT tray.tray_number INTO NEW.tray_number
    FROM tray
    WHERE tray.id = NEW.tray_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_splice_tray_number
BEFORE INSERT OR UPDATE OF tray_id ON splice
FOR EACH ROW EXECUTE FUNCTION sync_splice_tray_number();

CREATE FUNCTION assert_fiber_has_two_terminations() RETURNS trigger AS $$
DECLARE
    target_fiber_id UUID;
    termination_count INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'fiber' THEN
        IF TG_OP = 'DELETE' THEN
            target_fiber_id := OLD.id;
        ELSE
            target_fiber_id := NEW.id;
        END IF;
    ELSE
        IF TG_OP = 'DELETE' THEN
            target_fiber_id := OLD.fiber_id;
        ELSE
            target_fiber_id := NEW.fiber_id;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM fiber WHERE id = target_fiber_id) THEN
        SELECT COUNT(*) INTO termination_count
        FROM fiber_termination
        WHERE fiber_id = target_fiber_id;

        IF termination_count <> 2 THEN
            RAISE EXCEPTION 'Fiber % must have exactly two terminations', target_fiber_id
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_fiber_has_two_terminations
AFTER INSERT OR UPDATE OR DELETE ON fiber
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_fiber_has_two_terminations();

CREATE CONSTRAINT TRIGGER trg_termination_keeps_two_ends
AFTER INSERT OR UPDATE OR DELETE ON fiber_termination
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_fiber_has_two_terminations();

CREATE FUNCTION validate_optical_connection() RETURNS trigger AS $$
DECLARE
    first_fiber UUID;
    second_fiber UUID;
    first_status VARCHAR(16);
    second_status VARCHAR(16);
BEGIN
    SELECT fiber_id, status INTO first_fiber, first_status
    FROM fiber_termination WHERE id = NEW.termination_a_id;
    SELECT fiber_id, status INTO second_fiber, second_status
    FROM fiber_termination WHERE id = NEW.termination_b_id;

    IF first_fiber = second_fiber THEN
        RAISE EXCEPTION 'A fusion must connect terminations from different fibers'
            USING ERRCODE = '23514';
    END IF;
    IF first_status IN ('BROKEN', 'RESERVED') OR second_status IN ('BROKEN', 'RESERVED') THEN
        RAISE EXCEPTION 'Broken or reserved terminations cannot be connected'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_optical_connection
BEFORE INSERT OR UPDATE ON optical_connection
FOR EACH ROW EXECUTE FUNCTION validate_optical_connection();
