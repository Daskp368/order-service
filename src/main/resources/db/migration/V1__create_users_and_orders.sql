CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL,

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_username_length
        CHECK (char_length(username) BETWEEN 3 AND 50),
    CONSTRAINT chk_users_username_format
        CHECK (username ~ '^[a-z0-9_]+$'),
    CONSTRAINT chk_users_password_hash_length
        CHECK (char_length(password_hash) = 60),
    CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_orders_description_not_blank
        CHECK (char_length(btrim(description)) BETWEEN 1 AND 1000),
    CONSTRAINT chk_orders_status
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
