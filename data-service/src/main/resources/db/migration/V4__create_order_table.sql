CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id UUID NOT NULL,

    reference VARCHAR(25) NOT NULL UNIQUE,

    origin_id UUID NOT NULL REFERENCES operational_units(id),
    destination_id UUID REFERENCES operational_units(id),

    order_type VARCHAR(10) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',

    notes TEXT,

    tracking_token VARCHAR(64) UNIQUE,

    recipient_email VARCHAR(255),
    recipient_name VARCHAR(255),

    recipient_address VARCHAR(500),
    recipient_latitude NUMERIC(9,6),
    recipient_longitude NUMERIC(9,6),

    current_lat NUMERIC(9,6),
    current_lon NUMERIC(9,6),
    current_location_at TIMESTAMP,

    claimed BOOLEAN NOT NULL DEFAULT FALSE,

    loyal_user_id UUID,

    created_at TIMESTAMP NOT NULL DEFAULT now()
);


