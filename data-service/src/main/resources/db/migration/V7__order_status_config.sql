CREATE TABLE order_status_config (
    status       VARCHAR(50)  PRIMARY KEY,
    ui_severity  VARCHAR(20)  NOT NULL,
    allowed_transitions VARCHAR(500) NOT NULL DEFAULT '',
    is_terminal  BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INT          NOT NULL
);

INSERT INTO order_status_config (status, ui_severity, allowed_transitions, is_terminal, sort_order) VALUES
    ('PENDING',    'warn',    'IN_TRANSIT,CANCELLED', FALSE, 1),
    ('IN_TRANSIT', 'info',    'DELIVERED,CANCELLED',  FALSE, 2),
    ('DELIVERED',  'success', '',                     TRUE,  3),
    ('CANCELLED',  'danger',  '',                     TRUE,  4);
