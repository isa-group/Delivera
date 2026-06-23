CREATE TABLE vehicles (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID          NOT NULL REFERENCES companies(id),
    depot_id    UUID          NOT NULL REFERENCES operational_units(id),
    plate       VARCHAR(20)   NOT NULL,
    capacity    INTEGER       NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (company_id, plate)
);

CREATE INDEX idx_vehicles_company_id ON vehicles(company_id);
CREATE INDEX idx_vehicles_depot_id ON vehicles(depot_id);
