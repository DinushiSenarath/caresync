CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(120),
    role            VARCHAR(20)  NOT NULL CHECK (role IN
        ('ADMIN','RECEPTIONIST','DOCTOR','NURSE','PHARMACIST','CASHIER','PATIENT','LAB_TECH','WARD_STAFF')),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE doctors (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL UNIQUE REFERENCES users(id),
    department_id BIGINT NOT NULL REFERENCES departments(id),
    specialty     VARCHAR(100),
    work_days     VARCHAR(30) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    start_time    TIME NOT NULL DEFAULT '09:00',
    end_time      TIME NOT NULL DEFAULT '17:00',
    slot_minutes  INT  NOT NULL DEFAULT 15 CHECK (slot_minutes > 0),
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE SEQUENCE patient_no_seq START 1;

CREATE TABLE patients (
    id                      BIGSERIAL PRIMARY KEY,
    patient_no              VARCHAR(20) NOT NULL UNIQUE,
    user_id                 BIGINT UNIQUE REFERENCES users(id),
    full_name               VARCHAR(120) NOT NULL,
    date_of_birth           DATE NOT NULL,
    gender                  VARCHAR(10) NOT NULL CHECK (gender IN ('MALE','FEMALE','OTHER')),
    nic                     VARCHAR(20),
    phone                   VARCHAR(20) NOT NULL,
    address                 VARCHAR(255),
    emergency_contact_name  VARCHAR(120),
    emergency_contact_phone VARCHAR(20),
    allergies               TEXT,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_by              BIGINT REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_patients_name  ON patients (lower(full_name));
CREATE INDEX idx_patients_phone ON patients (phone);
CREATE INDEX idx_patients_nic   ON patients (nic);

CREATE TABLE appointments (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT NOT NULL REFERENCES patients(id),
    doctor_id        BIGINT NOT NULL REFERENCES doctors(id),
    appointment_date DATE NOT NULL,
    start_time       TIME NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN
        ('REQUESTED','SCHEDULED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','MISSED')),
    reason           VARCHAR(255),
    cancel_reason    VARCHAR(255),
    created_by       BIGINT REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- stops two active appointments for the same doctor, date and time
CREATE UNIQUE INDEX ux_appointment_slot
    ON appointments (doctor_id, appointment_date, start_time)
    WHERE status <> 'CANCELLED';

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);