CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,

    credential_id UUID NOT NULL,

    secret_hash VARCHAR(255) NOT NULL,

    expired_at TIMESTAMP,

    revoked BOOLEAN DEFAULT FALSE,

    suspicious BOOLEAN DEFAULT FALSE,

    user_agent TEXT,

    device VARCHAR(255),

    ip VARCHAR(45),

    last_used TIMESTAMP,

    CONSTRAINT fk_refresh_token_credential
        FOREIGN KEY (credential_id)
        REFERENCES credentials(user_id)
        ON DELETE CASCADE
);
