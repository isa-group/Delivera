CREATE INDEX idx_orders_company_id
ON orders(company_id);

CREATE INDEX idx_orders_loyal_user
ON orders(loyal_user_id);

CREATE INDEX idx_orders_claimed
ON orders(claimed);

CREATE INDEX idx_orders_status
ON orders(status);

CREATE INDEX idx_orders_priority
ON orders(priority);

CREATE INDEX idx_orders_created_at
ON orders(created_at);

CREATE INDEX idx_orders_tracking_token
ON orders(tracking_token);

CREATE INDEX idx_order_events_order_id
ON order_events(order_id);

CREATE INDEX idx_order_messages_order_id
ON order_messages(order_id);

CREATE INDEX idx_orders_company_status
ON orders(company_id, status);