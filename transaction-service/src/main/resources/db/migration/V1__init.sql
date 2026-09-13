CREATE TABLE transactions (
    id                 UUID PRIMARY KEY,
    idempotency_key    VARCHAR(255)   NOT NULL,
    source_account_id  UUID           NOT NULL,
    target_account_id  UUID           NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL,
    type               VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMP      NOT NULL,
    completed_at       TIMESTAMP,
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_transactions_source_account ON transactions (source_account_id);
CREATE INDEX idx_transactions_target_account ON transactions (target_account_id);
