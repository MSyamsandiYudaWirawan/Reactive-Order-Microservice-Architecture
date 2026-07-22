CREATE TABLE IF NOT EXISTS payments
(
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         VARCHAR(255)  NOT NULL,
    transaction_id  VARCHAR(255)  NOT NULL,
    correlation_id  VARCHAR(255)  NOT NULL,
    payment_method  VARCHAR(50)   NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    status          VARCHAR(50)   NOT NULL,
    failure_code    VARCHAR(100),
    failure_message VARCHAR(500),
    created_by      VARCHAR(255)  NOT NULL,
    updated_by      VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payment_ledger
(
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    payment_id     VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    status         VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS outbox
(
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
ALTER USER username REPLICATION;
