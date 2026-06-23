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
    id                   UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL,
    correlation_id       VARCHAR(255) NOT NULL,
    event_type           VARCHAR(255) NOT NULL, -- PENDING, WAITING_PAYMENT, PAID, COMPLETED, FAILED, REFUNDED
    created_date         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
