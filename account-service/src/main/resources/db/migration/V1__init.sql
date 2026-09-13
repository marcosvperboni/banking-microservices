CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    customer_id    UUID           NOT NULL,
    account_number VARCHAR(10)    NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    balance        NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
