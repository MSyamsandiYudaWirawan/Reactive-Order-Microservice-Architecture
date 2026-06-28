CREATE TABLE IF NOT EXISTS products(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL NOT NULL ,
    available_qty INTEGER NOT NULL ,
    reserved_qty INTEGER NOT NULL ,
    sold_qty INTEGER NOT NULL ,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255) ,
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS stock_reservation(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY ,
    product_id VARCHAR(255) NOT NULL ,
    transaction_id VARCHAR(255) NOT NULL ,
    correlation_id VARCHAR(255) NOT NULL,
    qty INTEGER NOT NULL ,
    status VARCHAR(255) NOT NULL ,
    created_by         VARCHAR(255) NOT NULL,
    updated_by         VARCHAR(255),
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS stock_ledger(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL ,
    transaction_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    qty INTEGER NOT NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Sample products for testing
INSERT INTO products (id, name, price, available_qty, reserved_qty, sold_qty, description, is_active, is_deleted, created_by)
VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Wireless Mouse', 29.99, 100, 0, 0, 'Ergonomic wireless mouse with USB receiver', TRUE, FALSE, 'SYSTEM'),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'Mechanical Keyboard', 89.99, 50, 0, 0, 'RGB mechanical keyboard with Cherry MX switches', TRUE, FALSE, 'SYSTEM'),
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'USB-C Hub', 49.99, 75, 0, 0, '7-in-1 USB-C hub with HDMI and ethernet', TRUE, FALSE, 'SYSTEM'),
    ('d4e5f6a7-b8c9-0123-defa-234567890123', 'Monitor Stand', 39.99, 30, 0, 0, 'Adjustable aluminum monitor stand', TRUE, FALSE, 'SYSTEM'),
    ('e5f6a7b8-c9d0-1234-efab-345678901234', 'Webcam HD', 59.99, 60, 0, 0, '1080p webcam with built-in microphone', TRUE, FALSE, 'SYSTEM');

CREATE INDEX idx_stock_reservation_transaction_id ON stock_reservation(transaction_id);