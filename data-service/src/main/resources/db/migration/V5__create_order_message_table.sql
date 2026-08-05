CREATE TABLE order_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    order_id UUID NOT NULL
        REFERENCES orders(id)
        ON DELETE CASCADE,

    sender_id UUID NOT NULL,

    content TEXT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT now()
);