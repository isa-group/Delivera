DROP INDEX IF EXISTS idx_orders_delivery_window;

CREATE INDEX idx_orders_delivery_window
ON orders(
    company_id,
    status,
    from_date,
    to_date
);