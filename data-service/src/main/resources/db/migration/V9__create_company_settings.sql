CREATE TABLE company_settings (
    company_id UUID PRIMARY KEY,
    
    org_id UUID NOT NULL,

    default_priority VARCHAR(10),

    default_priority_locked BOOLEAN NOT NULL DEFAULT FALSE
);