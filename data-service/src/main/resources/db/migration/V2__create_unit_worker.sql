CREATE TABLE unit_workers (
    id UUID PRIMARY KEY,

    unit_id UUID NOT NULL,

    worker_id UUID NOT NULL,

    company_id UUID NOT NULL,

    user_id UUID NOT NULL,

    CONSTRAINT fk_unit_workers_unit
        FOREIGN KEY (unit_id)
        REFERENCES operational_units(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_unit_workers_unit
    ON unit_workers(unit_id);

CREATE INDEX idx_unit_workers_worker
    ON unit_workers(worker_id);

CREATE INDEX idx_unit_workers_company
    ON unit_workers(company_id);

CREATE INDEX idx_unit_workers_user
    ON unit_workers(user_id);

CREATE INDEX idx_operational_units_company_org
ON operational_units(company_id, org_id);

ALTER TABLE unit_workers
ADD CONSTRAINT uk_unit_workers_unit_worker
UNIQUE(unit_id, worker_id);