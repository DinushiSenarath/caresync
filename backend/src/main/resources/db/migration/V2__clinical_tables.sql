CREATE TABLE consultation_notes (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id),
    author_id      BIGINT NOT NULL REFERENCES users(id),
    symptoms       TEXT,
    findings       TEXT,
    diagnosis      TEXT,
    treatment_plan TEXT,
    status         VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','FINAL')),
    finalised_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vital_signs (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    recorded_by    BIGINT NOT NULL REFERENCES users(id),
    temperature    NUMERIC(4,1),
    pulse          INT,
    bp_systolic    INT,
    bp_diastolic   INT,
    resp_rate      INT,
    spo2           INT,
    weight         NUMERIC(5,1),
    height         NUMERIC(5,1),
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vitals_appointment ON vital_signs (appointment_id);

CREATE TABLE nursing_notes (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    nurse_id       BIGINT NOT NULL REFERENCES users(id),
    note           TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nursing_notes_appointment ON nursing_notes (appointment_id);