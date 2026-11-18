CREATE TABLE medicines (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    strength      VARCHAR(50),
    form          VARCHAR(30)  NOT NULL CHECK (form IN
        ('TABLET','CAPSULE','SYRUP','INJECTION','CREAM','DROPS','INHALER','OTHER')),
    unit          VARCHAR(20)  NOT NULL,
    category      VARCHAR(60),
    reorder_level INT NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_medicines_identity ON medicines (lower(name), lower(coalesce(strength, '')), form);

CREATE TABLE medicine_batches (
    id          BIGSERIAL PRIMARY KEY,
    medicine_id BIGINT NOT NULL REFERENCES medicines(id),
    batch_no    VARCHAR(50) NOT NULL,
    expiry_date DATE NOT NULL,
    quantity    INT NOT NULL CHECK (quantity >= 0),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (medicine_id, batch_no)
);
CREATE INDEX idx_batches_medicine_expiry ON medicine_batches (medicine_id, expiry_date);

CREATE TABLE stock_movements (
    id         BIGSERIAL PRIMARY KEY,
    batch_id   BIGINT NOT NULL REFERENCES medicine_batches(id),
    type       VARCHAR(15) NOT NULL CHECK (type IN ('RECEIPT','DISPENSE','ADJUSTMENT','RETURN')),
    quantity   INT NOT NULL CHECK (quantity <> 0),
    reference  VARCHAR(100),
    user_id    BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movements_batch ON stock_movements (batch_id);

CREATE TABLE prescriptions (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    doctor_id      BIGINT NOT NULL REFERENCES doctors(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN
        ('DRAFT','ISSUED','PARTIALLY_DISPENSED','DISPENSED','CANCELLED')),
    notes          VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_at      TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_prescriptions_appointment ON prescriptions (appointment_id);

CREATE TABLE prescription_items (
    id              BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT NOT NULL REFERENCES prescriptions(id),
    medicine_id     BIGINT NOT NULL REFERENCES medicines(id),
    dose            VARCHAR(50) NOT NULL,
    route           VARCHAR(30),
    frequency       VARCHAR(50) NOT NULL,
    duration        VARCHAR(50),
    instructions    VARCHAR(255),
    qty_prescribed  INT NOT NULL CHECK (qty_prescribed > 0),
    qty_dispensed   INT NOT NULL DEFAULT 0 CHECK (qty_dispensed >= 0),
    CHECK (qty_dispensed <= qty_prescribed)
);
CREATE INDEX idx_rx_items_prescription ON prescription_items (prescription_id);

CREATE TABLE dispensed_items (
    id                   BIGSERIAL PRIMARY KEY,
    prescription_item_id BIGINT NOT NULL REFERENCES prescription_items(id),
    batch_id             BIGINT NOT NULL REFERENCES medicine_batches(id),
    quantity             INT NOT NULL CHECK (quantity > 0),
    dispensed_by         BIGINT NOT NULL REFERENCES users(id),
    dispensed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);