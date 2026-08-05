CREATE TABLE order_priority_config (
    priority     VARCHAR(50) PRIMARY KEY,
    ui_severity  VARCHAR(20) NOT NULL,
    sort_order   INT         NOT NULL
);

INSERT INTO order_priority_config (priority, ui_severity, sort_order) VALUES
    ('HIGH',   'danger',    1),
    ('NORMAL', 'secondary', 2),
    ('LOW',    'info',      3);
