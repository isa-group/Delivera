CREATE TABLE company_settings (
    company_id UUID PRIMARY KEY,

    default_priority VARCHAR(10),

    default_priority_locked BOOLEAN NOT NULL DEFAULT FALSE
);