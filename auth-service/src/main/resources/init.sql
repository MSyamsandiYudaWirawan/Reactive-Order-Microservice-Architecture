CREATE TABLE if NOT EXISTS users (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    phonenumber VARCHAR(255) NOT NULL UNIQUE,
    roles VARCHAR(255),
    created_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMPTZ,
    is_locked BOOLEAN DEFAULT FALSE,
    is_credentials_expired BOOLEAN DEFAULT FALSE,
    is_enabled BOOLEAN DEFAULT FALSE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    is_phone_verified BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE
);
