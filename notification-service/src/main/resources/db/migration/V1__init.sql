CREATE TABLE notifications (
    id             UUID PRIMARY KEY,
    account_id     UUID NOT NULL,
    transaction_id UUID NOT NULL,
    channel        VARCHAR(20) NOT NULL,
    message        VARCHAR(500) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notifications_account_id ON notifications (account_id);
