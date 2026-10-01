ALTER TABLE orders
ADD COLUMN fromDate TIMESTAMP;

ALTER TABLE orders
ADD COLUMN toDate TIMESTAMP;

CREATE INDEX idx_orders_delivery_window
ON orders(
    fromDate,
    toDate
);