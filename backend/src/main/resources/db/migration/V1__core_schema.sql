-- OpenEx 3.0 :: Week 1 core schema (users, immutable double-entry ledger, orders, trades)

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- One account per (user, asset). type = USER | SYSTEM (the faucet / house account)
CREATE TABLE accounts (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    asset      VARCHAR(16) NOT NULL,
    type       VARCHAR(16) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_accounts_user_asset UNIQUE (user_id, asset)
);


-- Append-only. No UPDATE, no DELETE. Ever.
CREATE TABLE ledger_entries (
    id             UUID           PRIMARY KEY,
    transaction_id UUID           NOT NULL,
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    asset          VARCHAR(16)    NOT NULL,
    direction      VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    memo           VARCHAR(255),
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_ledger_account ON ledger_entries (account_id, asset);
CREATE INDEX idx_ledger_tx ON ledger_entries (transaction_id);

CREATE TABLE orders (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL REFERENCES users (id),
    symbol          VARCHAR(32)     NOT NULL,
    side            VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type            VARCHAR(8)      NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    price           NUMERIC(38, 18),
    quantity        NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    filled_quantity NUMERIC(38, 18) NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_symbol_status ON orders (symbol, status);

CREATE TABLE trades (
    id            UUID            PRIMARY KEY,
    symbol        VARCHAR(32)     NOT NULL,
    buy_order_id  UUID            NOT NULL REFERENCES orders (id),
    sell_order_id UUID            NOT NULL REFERENCES orders (id),
    price         NUMERIC(38, 18) NOT NULL CHECK (price > 0),
    quantity      NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_keys (
    id            UUID         PRIMARY KEY,
    idem_key      VARCHAR(128) NOT NULL,
    user_id       UUID         NOT NULL REFERENCES users (id),
    request_hash  VARCHAR(128) NOT NULL,
    response_body TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idem_user_key UNIQUE (user_id, idem_key)
);
