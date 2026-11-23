CREATE SEQUENCE employee_no_seq START 1;

CREATE TABLE employees (
    id                      BIGSERIAL PRIMARY KEY,
    employee_no             VARCHAR(20) NOT NULL UNIQUE,
    full_name               VARCHAR(120) NOT NULL,
    nic                     VARCHAR(20),
    phone                   VARCHAR(20) NOT NULL,
    email                   VARCHAR(120),
    address                 VARCHAR(255),
    job_title               VARCHAR(100) NOT NULL,
    department_id           BIGINT NOT NULL REFERENCES departments(id),
    employment_type         VARCHAR(12) NOT NULL CHECK (employment_type IN ('PERMANENT','CONTRACT','PART_TIME','INTERN')),
    hire_date               DATE NOT NULL,
    user_id                 BIGINT UNIQUE REFERENCES users(id),
    emergency_contact_name  VARCHAR(120),
    emergency_contact_phone VARCHAR(20),
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_employees_nic ON employees (nic) WHERE nic IS NOT NULL;
CREATE INDEX idx_employees_department ON employees (department_id);

CREATE TABLE attendance_records (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    work_date   DATE NOT NULL,
    check_in    TIMESTAMPTZ,
    check_out   TIMESTAMPTZ,
    status      VARCHAR(10) NOT NULL CHECK (status IN ('PRESENT','ABSENT','HALF_DAY')),
    source      VARCHAR(10) NOT NULL CHECK (source IN ('SELF','MANUAL')),
    note        VARCHAR(255),
    recorded_by BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (employee_id, work_date),
    CHECK (check_out IS NULL OR check_in IS NULL OR check_out >= check_in),
    CHECK (status = 'ABSENT' OR check_in IS NOT NULL),
    CHECK (status <> 'ABSENT' OR (check_in IS NULL AND check_out IS NULL))
);
CREATE INDEX idx_attendance_date ON attendance_records (work_date);

CREATE TABLE leave_requests (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT NOT NULL REFERENCES employees(id),
    leave_type    VARCHAR(10) NOT NULL CHECK (leave_type IN ('ANNUAL','CASUAL','SICK','UNPAID','OTHER')),
    from_date     DATE NOT NULL,
    to_date       DATE NOT NULL,
    days          INT NOT NULL CHECK (days > 0),
    reason        VARCHAR(255),
    status        VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    requested_by  BIGINT NOT NULL REFERENCES users(id),
    decided_by    BIGINT REFERENCES users(id),
    decision_note VARCHAR(255),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (to_date >= from_date),
    CHECK (date_part('year', from_date) = date_part('year', to_date))
);
CREATE INDEX idx_leave_employee ON leave_requests (employee_id, from_date);
CREATE INDEX idx_leave_status ON leave_requests (status, created_at);