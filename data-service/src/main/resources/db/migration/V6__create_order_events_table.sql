CREATE TABLE order_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    order_id UUID NOT NULL
        REFERENCES orders(id)
        ON DELETE CASCADE,

    status VARCHAR(20) NOT NULL,

    note TEXT,

    author_email VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT now()
);