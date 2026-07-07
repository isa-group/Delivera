-- Move per-customer profile fields (name, phone, address, lat/lon) from the shared
-- loyal_users table to the per-company link table so each company keeps its own copy.
-- The legacy columns on loyal_users are left in place as backup (no longer mapped).

ALTER TABLE loyal_user_companies
    ADD COLUMN name       VARCHAR(255),
    ADD COLUMN phone      VARCHAR(20),
    ADD COLUMN address    VARCHAR(500),
    ADD COLUMN latitude   DECIMAL(9,6),
    ADD COLUMN longitude  DECIMAL(9,6),
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE loyal_user_companies luc
SET name       = lu.name,
    phone      = lu.phone,
    address    = lu.address,
    latitude   = lu.latitude,
    longitude  = lu.longitude,
    created_at = COALESCE(lu.created_at, CURRENT_TIMESTAMP)
FROM loyal_users lu
WHERE luc.loyal_user_id = lu.id;
