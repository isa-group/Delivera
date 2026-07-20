ALTER TABLE refresh_token ADD COLUMN max_expired_at TIMESTAMP;
ALTER TABLE refresh_token ADD COLUMN created_at TIMESTAMP;