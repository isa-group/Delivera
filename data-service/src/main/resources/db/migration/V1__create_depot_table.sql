CREATE TABLE operational_units (
    id UUID PRIMARY KEY,

    company_id UUID NOT NULL,
    org_id UUID NOT NULL,

    name VARCHAR(255) NOT NULL,

    type VARCHAR(50) NOT NULL,

    address VARCHAR(500),

    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    default_priority VARCHAR(10)
);

CREATE INDEX idx_operational_units_company
    ON operational_units(company_id);

CREATE INDEX idx_operational_units_org
    ON operational_units(org_id);