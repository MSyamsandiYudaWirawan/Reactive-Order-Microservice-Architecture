CREATE TABLE IF NOT EXISTS saga_state
(
    id                 UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_id     VARCHAR(255) NOT NULL,
    correlation_id     VARCHAR(255) NOT NULL,
    stock_status       VARCHAR(255) NULL,     --NULL → RESERVED / OUT_OF_STOCK
    payment_status     VARCHAR(255) NULL,     --NULL → INITIATED / PAID / FAILED
    saga_status        VARCHAR(255) NOT NULL, --IN_PROGRESS / COMPLETED / COMPENSATING / FAILED
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255),
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ
)