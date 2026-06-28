CREATE TABLE IF NOT EXISTS payments
(
    id                 UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id            VARCHAR(255) NOT NULL,
    transaction_id     VARCHAR(255) NOT NULL,
    correlation_id     VARCHAR(255) NOT NULL,
    payment_method     VARCHAR(50)  NOT NULL,
    amount             DECIMAL      NOT NULL,
    status             VARCHAR(50)  NOT NULL,
    failure_code       VARCHAR(100),
    failure_message    VARCHAR(500),
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255),
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payment_ledger
(
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    payment_id     VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    created_date   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);