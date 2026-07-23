CREATE TABLE IF NOT EXISTS saga_state
(
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    payment_id     VARCHAR(255) NULL,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    correlation_id VARCHAR(255) NOT NULL,
    stock_status   VARCHAR(255) NULL,
    payment_status VARCHAR(255) NULL,
    saga_status    VARCHAR(255) NOT NULL,
    created_by     VARCHAR(255) NOT NULL,
    updated_by     VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ
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

ALTER USER username REPLICATION;