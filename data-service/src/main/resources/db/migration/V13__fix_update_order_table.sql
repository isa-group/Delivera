DROP INDEX IF EXISTS idx_orders_delivery_window;

ALTER TABLE orders
DROP COLUMN IF EXISTS fromDate;

ALTER TABLE orders
DROP COLUMN IF EXISTS toDate;

ALTER TABLE orders
ADD COLUMN from_date TIMESTAMP;

ALTER TABLE orders
ADD COLUMN to_date TIMESTAMP;

CREATE INDEX idx_orders_delivery_window
ON orders(
    from_date,
    to_date
);