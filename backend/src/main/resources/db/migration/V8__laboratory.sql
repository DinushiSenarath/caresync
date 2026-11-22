CREATE TABLE lab_tests (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    category    VARCHAR(20)  NOT NULL CHECK (category IN
        ('HAEMATOLOGY','BIOCHEMISTRY','MICROBIOLOGY','URINALYSIS','IMMUNOLOGY','OTHER')),
    specimen    VARCHAR(40)  NOT NULL,
    result_type VARCHAR(10)  NOT NULL CHECK (result_type IN ('NUMERIC','TEXT')),
    unit        VARCHAR(30),
    ref_low     NUMERIC(12,3),
    ref_high    NUMERIC(12,3),
    ref_text    VARCHAR(120),
    price       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ref_low IS NULL OR ref_high IS NULL OR ref_low <= ref_high)
);
CREATE UNIQUE INDEX ux_lab_tests_name ON lab_tests (lower(name));

CREATE SEQUENCE lab_request_no_seq START 1;

CREATE TABLE lab_requests (
    id             BIGSERIAL PRIMARY KEY,
    request_no     VARCHAR(20) NOT NULL UNIQUE,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    patient_id     BIGINT NOT NULL REFERENCES patients(id),
    requested_by   BIGINT NOT NULL REFERENCES users(id),
    priority       VARCHAR(10) NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('ROUTINE','URGENT')),
    clinical_notes VARCHAR(500),
    status         VARCHAR(12) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','COLLECTED','VERIFIED','CANCELLED')),
    sample_no      VARCHAR(40),
    collected_by   BIGINT REFERENCES users(id),
    collected_at   TIMESTAMPTZ,
    verified_by    BIGINT REFERENCES users(id),
    verified_at    TIMESTAMPTZ,
    cancel_reason  VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lab_requests_status ON lab_requests (status, created_at);
CREATE INDEX idx_lab_requests_appointment ON lab_requests (appointment_id);
CREATE INDEX idx_lab_requests_patient ON lab_requests (patient_id);

CREATE TABLE lab_request_items (
    id             BIGSERIAL PRIMARY KEY,
    request_id     BIGINT NOT NULL REFERENCES lab_requests(id),
    test_id        BIGINT NOT NULL REFERENCES lab_tests(id),
    test_name      VARCHAR(120) NOT NULL,
    specimen       VARCHAR(40)  NOT NULL,
    result_type    VARCHAR(10)  NOT NULL,
    unit           VARCHAR(30),
    ref_low        NUMERIC(12,3),
    ref_high       NUMERIC(12,3),
    ref_text       VARCHAR(120),
    price          NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    result_value   VARCHAR(500),
    result_number  NUMERIC(14,4),
    flag           VARCHAR(10) CHECK (flag IN ('NORMAL','LOW','HIGH')),
    result_comment VARCHAR(500),
    entered_by     BIGINT REFERENCES users(id),
    entered_at     TIMESTAMPTZ
);
CREATE INDEX idx_lab_items_request ON lab_request_items (request_id);

-- allow lab lines on invoices (drops whatever check exists on item_type, then adds the new one)
DO $$
DECLARE c text;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'invoice_items'::regclass AND contype = 'c'
             AND pg_get_constraintdef(oid) LIKE '%item_type%'
  LOOP
    EXECUTE format('ALTER TABLE invoice_items DROP CONSTRAINT %I', c);
  END LOOP;
END $$;
ALTER TABLE invoice_items
    ADD CONSTRAINT invoice_items_item_type_check CHECK (item_type IN ('SERVICE','MEDICINE','LAB'));