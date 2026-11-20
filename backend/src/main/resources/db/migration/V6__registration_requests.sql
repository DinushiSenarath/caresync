CREATE TABLE registration_requests (
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender        VARCHAR(10) NOT NULL CHECK (gender IN ('MALE','FEMALE','OTHER')),
    nic           VARCHAR(20),
    phone         VARCHAR(20) NOT NULL,
    address       VARCHAR(255),
    status        VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    patient_id    BIGINT REFERENCES patients(id),
    decision_note VARCHAR(255),
    decided_by    BIGINT REFERENCES users(id),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_registration_status ON registration_requests (status, created_at);
CREATE UNIQUE INDEX ux_registration_pending_phone ON registration_requests (phone) WHERE status = 'PENDING';
CREATE UNIQUE INDEX ux_registration_pending_nic ON registration_requests (nic) WHERE status = 'PENDING' AND nic IS NOT NULL;