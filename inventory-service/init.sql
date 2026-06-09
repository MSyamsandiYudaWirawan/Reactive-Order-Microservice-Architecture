CREATE TABLE IF NOT EXISTS products(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL NOT NULL ,
    available_qty INTEGER NOT NULL ,
    reserved_qty INTEGER NOT NULL ,
    sold_qty INTEGER NOT NULL ,
    description TEXT,
    is_active BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255) NOT NULL,
    created_date       TIMESTAMPTZ  NOT NULL,
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS stock_reservation(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY ,
    product_id VARCHAR(255) NOT NULL ,
    order_transaction_id VARCHAR(255) NOT NULL ,
    correlation_id VARCHAR(255) NOT NULL,
    qty INTEGER NOT NULL ,
    status VARCHAR(255) NOT NULL ,
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255) NOT NULL,
    created_date       TIMESTAMPTZ  NOT NULL,
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS stock_ledger(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL ,
    order_transaction_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    qty INTEGER NOT NULL,
    created_date TIMESTAMPTZ NOT NULL
);