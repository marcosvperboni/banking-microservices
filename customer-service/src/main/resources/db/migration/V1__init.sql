CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    document_number VARCHAR(11)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE customer_roles (
    customer_id UUID NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    role        VARCHAR(50) NOT NULL,
    PRIMARY KEY (customer_id, role)
);
