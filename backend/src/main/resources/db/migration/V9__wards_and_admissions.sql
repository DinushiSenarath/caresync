CREATE TABLE wards (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    ward_type   VARCHAR(15) NOT NULL CHECK (ward_type IN ('GENERAL','PRIVATE','ICU','MATERNITY','PAEDIATRIC','OTHER')),
    description VARCHAR(255),
    daily_rate  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (daily_rate >= 0),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_wards_name ON wards (lower(name));

CREATE TABLE beds (
    id      BIGSERIAL PRIMARY KEY,
    ward_id BIGINT NOT NULL REFERENCES wards(id),
    bed_no  VARCHAR(20) NOT NULL,
    active  BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_beds_ward_no ON beds (ward_id, lower(bed_no));

CREATE SEQUENCE admission_no_seq START 1;

CREATE TABLE admissions (
    id                     BIGSERIAL PRIMARY KEY,
    admission_no           VARCHAR(20) NOT NULL UNIQUE,
    patient_id             BIGINT NOT NULL REFERENCES patients(id),
    responsible_doctor_id  BIGINT NOT NULL REFERENCES doctors(id),
    appointment_id         BIGINT REFERENCES appointments(id),
    reason                 VARCHAR(500) NOT NULL,
    status                 VARCHAR(12) NOT NULL DEFAULT 'ADMITTED' CHECK (status IN ('ADMITTED','DISCHARGED','CANCELLED')),
    admitted_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    admitted_by            BIGINT NOT NULL REFERENCES users(id),
    discharge_summary      TEXT,
    discharge_instructions TEXT,
    summary_by             BIGINT REFERENCES users(id),
    summary_at             TIMESTAMPTZ,
    discharged_at          TIMESTAMPTZ,
    discharged_by          BIGINT REFERENCES users(id),
    cancel_reason          VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'DISCHARGED' OR (discharged_at IS NOT NULL AND discharge_summary IS NOT NULL))
);
CREATE UNIQUE INDEX ux_admissions_one_active_per_patient ON admissions (patient_id) WHERE status = 'ADMITTED';
CREATE INDEX idx_admissions_status ON admissions (status, admitted_at);

CREATE TABLE bed_stays (
    id              BIGSERIAL PRIMARY KEY,
    admission_id    BIGINT NOT NULL REFERENCES admissions(id),
    bed_id          BIGINT NOT NULL REFERENCES beds(id),
    ward_name       VARCHAR(100) NOT NULL,
    bed_no          VARCHAR(20) NOT NULL,
    daily_rate      NUMERIC(12,2) NOT NULL CHECK (daily_rate >= 0),
    from_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    to_at           TIMESTAMPTZ,
    transfer_reason VARCHAR(255),
    moved_by        BIGINT NOT NULL REFERENCES users(id),
    CHECK (to_at IS NULL OR to_at >= from_at)
);
CREATE UNIQUE INDEX ux_bed_one_occupant ON bed_stays (bed_id) WHERE to_at IS NULL;
CREATE UNIQUE INDEX ux_admission_one_open_stay ON bed_stays (admission_id) WHERE to_at IS NULL;
CREATE INDEX idx_stays_admission ON bed_stays (admission_id);

-- an invoice can now belong to either a visit or an admission
ALTER TABLE invoices ALTER COLUMN appointment_id DROP NOT NULL;
ALTER TABLE invoices ADD COLUMN admission_id BIGINT REFERENCES admissions(id);
ALTER TABLE invoices ADD CONSTRAINT invoices_one_source CHECK (num_nonnulls(appointment_id, admission_id) = 1);
CREATE UNIQUE INDEX ux_invoice_active_per_admission ON invoices (admission_id)
    WHERE status <> 'VOIDED' AND admission_id IS NOT NULL;

-- allow bed lines on invoices
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
    ADD CONSTRAINT invoice_items_item_type_check CHECK (item_type IN ('SERVICE','MEDICINE','LAB','BED'));