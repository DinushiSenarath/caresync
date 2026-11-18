ALTER TABLE medicines ADD COLUMN unit_price NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0);

CREATE SEQUENCE invoice_no_seq START 1;
CREATE SEQUENCE receipt_no_seq START 1;
CREATE SEQUENCE refund_no_seq START 1;

CREATE TABLE service_charges (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    category   VARCHAR(20)  NOT NULL CHECK (category IN ('CONSULTATION','PROCEDURE','INVESTIGATION','OTHER')),
    price      NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_service_charges_name ON service_charges (lower(name));

CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    invoice_no     VARCHAR(20) NOT NULL UNIQUE,
    patient_id     BIGINT NOT NULL REFERENCES patients(id),
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    subtotal       NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    discount       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    total          NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    amount_paid    NUMERIC(12,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('UNPAID','PARTIALLY_PAID','PAID','VOIDED')),
    void_reason    VARCHAR(255),
    created_by     BIGINT NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (discount <= subtotal),
    CHECK (total = subtotal - discount),
    CHECK (amount_paid >= 0 AND amount_paid <= total)
);
CREATE UNIQUE INDEX ux_invoice_active_per_appointment ON invoices (appointment_id) WHERE status <> 'VOIDED';
CREATE INDEX idx_invoices_patient ON invoices (patient_id);

CREATE TABLE invoice_items (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id),
    item_type   VARCHAR(10) NOT NULL CHECK (item_type IN ('SERVICE','MEDICINE')),
    description VARCHAR(200) NOT NULL,
    quantity    INT NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    line_total  NUMERIC(12,2) NOT NULL CHECK (line_total >= 0)
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);

CREATE TABLE payments (
    id            BIGSERIAL PRIMARY KEY,
    receipt_no    VARCHAR(20) NOT NULL UNIQUE,
    invoice_id    BIGINT NOT NULL REFERENCES invoices(id),
    amount        NUMERIC(12,2) NOT NULL CHECK (amount <> 0),
    method        VARCHAR(20) NOT NULL CHECK (method IN ('CASH','CARD','BANK_TRANSFER','OTHER')),
    reference     VARCHAR(100),
    balance_after NUMERIC(12,2) NOT NULL,
    cashier_id    BIGINT NOT NULL REFERENCES users(id),
    paid_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_invoice ON payments (invoice_id);

CREATE TABLE invoice_adjustments (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    BIGINT NOT NULL REFERENCES invoices(id),
    type          VARCHAR(10) NOT NULL CHECK (type IN ('DISCOUNT','VOID','REFUND')),
    amount        NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    refund_method VARCHAR(20) CHECK (refund_method IN ('CASH','CARD','BANK_TRANSFER','OTHER')),
    reason        VARCHAR(255) NOT NULL,
    status        VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    requested_by  BIGINT NOT NULL REFERENCES users(id),
    decided_by    BIGINT REFERENCES users(id),
    decision_note VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_adjustment_one_pending ON invoice_adjustments (invoice_id) WHERE status = 'PENDING';