CREATE TABLE if NOT EXISTS orders
(
    id                 UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    correlation_id     VARCHAR(255) NOT NULL,
    transaction_id     VARCHAR(255) NOT NULL,
    user_id            VARCHAR(255) NOT NULL,
    discount_code      VARCHAR(255),
    order_status       VARCHAR(50)  NOT NULL,
    total_amount       DECIMAL      NOT NULL,
    failure_code       VARCHAR(100),
    failure_message    VARCHAR(500),
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255),
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE if NOT EXISTS order_items
(
    id                 UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    correlation_id     VARCHAR(255) NOT NULL,
    transaction_id     VARCHAR(255) NOT NULL,
    product_id         VARCHAR(255) NOT NULL,
    quantity           INTEGER      NOT NULL,
    price              DECIMAL      NOT NULL,
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255),
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS discounts
(
    id                  UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code                VARCHAR(50) UNIQUE NOT NULL,
    discount_type       VARCHAR(20)        NOT NULL, -- 'PERCENTAGE' or 'FIXED'
    value               DECIMAL            NOT NULL,
    minimum_order_value DECIMAL,
    maximum_order_value DECIMAL,
    max_usage           INTEGER,
    valid_from          TIMESTAMPTZ,
    valid_until         TIMESTAMPTZ,
    created_by          VARCHAR(255)       NOT NULL,
    updated_by          VARCHAR(255),
    created_date        TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    last_modified_date  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS order_ledger
(
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL, -- PENDING, WAITING_PAYMENT, PAID, COMPLETED, FAILED, REFUNDED
    created_date   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_transaction_id ON orders(transaction_id);

-- Sample discounts
INSERT INTO discounts (id, code, discount_type, value, minimum_order_value, maximum_order_value, max_usage, valid_from, valid_until, created_by)
SELECT 'f1a2b3c4-d5e6-7890-abcd-111111111111', 'WELCOME10', 'PERCENTAGE', 10, 50, NULL, 1000, NOW(), NOW() + INTERVAL '1 year', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM discounts WHERE code = 'WELCOME10');

INSERT INTO discounts (id, code, discount_type, value, minimum_order_value, maximum_order_value, max_usage, valid_from, valid_until, created_by)
SELECT 'f1a2b3c4-d5e6-7890-abcd-222222222222', 'SAVE20', 'PERCENTAGE', 20, 100, 500, 500, NOW(), NOW() + INTERVAL '6 months', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM discounts WHERE code = 'SAVE20');

INSERT INTO discounts (id, code, discount_type, value, minimum_order_value, maximum_order_value, max_usage, valid_from, valid_until, created_by)
SELECT 'f1a2b3c4-d5e6-7890-abcd-333333333333', 'FLAT15', 'FIXED', 15, 75, NULL, 2000, NOW(), NOW() + INTERVAL '1 year', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM discounts WHERE code = 'FLAT15');

INSERT INTO discounts (id, code, discount_type, value, minimum_order_value, maximum_order_value, max_usage, valid_from, valid_until, created_by)
SELECT 'f1a2b3c4-d5e6-7890-abcd-444444444444', 'MEGA50', 'FIXED', 50, 200, NULL, 100, NOW(), NOW() + INTERVAL '3 months', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM discounts WHERE code = 'MEGA50');

INSERT INTO discounts (id, code, discount_type, value, minimum_order_value, maximum_order_value, max_usage, valid_from, valid_until, created_by)
SELECT 'f1a2b3c4-d5e6-7890-abcd-555555555555', 'VIP30', 'PERCENTAGE', 30, 150, 1000, 50, NOW(), NOW() + INTERVAL '1 year', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM discounts WHERE code = 'VIP30');
