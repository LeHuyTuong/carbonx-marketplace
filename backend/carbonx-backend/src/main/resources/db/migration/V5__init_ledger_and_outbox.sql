-- Bounded Context 5: Ledger & Settlement (Immutable Wallet Ledger + Payout Provenance)
-- Plus Transactional Outbox & Notifications (Bounded Context 6)

CREATE TABLE IF NOT EXISTS wallets (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL UNIQUE,
    balance_usd          DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallets_non_negative CHECK (balance_usd >= 0)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id            BIGINT         NOT NULL,
    idempotency_key      VARCHAR(128)   NOT NULL UNIQUE,
    tx_type              VARCHAR(32)    NOT NULL, -- DEPOSIT, ORDER_DEBIT, ORDER_CREDIT, PLATFORM_FEE, PROFIT_PAYOUT, WITHDRAWAL
    amount_usd           DECIMAL(18, 4) NOT NULL,
    balance_before       DECIMAL(18, 4) NOT NULL,
    balance_after        DECIMAL(18, 4) NOT NULL,
    reference_type       VARCHAR(64),             -- TRADE_ORDER, PAYOUT_ITEM, WITHDRAWAL, PAYMENT_DEPOSIT
    reference_id         VARCHAR(128),
    description          VARCHAR(500),
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wtx_wallet (wallet_id),
    INDEX idx_wtx_ref (reference_type, reference_id),
    CONSTRAINT fk_wtx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE TABLE IF NOT EXISTS bank_accounts (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id            BIGINT       NOT NULL,
    bank_name            VARCHAR(128) NOT NULL,
    branch               VARCHAR(128),
    account_number       VARCHAR(64)  NOT NULL,
    account_holder       VARCHAR(191) NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE TABLE IF NOT EXISTS withdrawals (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id            BIGINT         NOT NULL,
    bank_account_id      BIGINT         NOT NULL,
    amount_usd           DECIMAL(18, 4) NOT NULL,
    status               VARCHAR(32)    NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, REJECTED
    processed_by_admin   BIGINT,
    requested_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at         TIMESTAMP      NULL,
    INDEX idx_withdrawals_status (status),
    CONSTRAINT fk_withdrawals_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_withdrawals_bank FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id)
);

CREATE TABLE IF NOT EXISTS profit_distributions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT         NOT NULL,
    source_report_id     BIGINT         NOT NULL UNIQUE,
    credit_batch_id      BIGINT         NOT NULL,
    batch_code_snap      VARCHAR(128)   NOT NULL,
    period               VARCHAR(32)    NOT NULL,
    unit_price_per_credit DECIMAL(18, 4) NOT NULL,
    total_payout_usd     DECIMAL(18, 4) NOT NULL,
    status               VARCHAR(32)    NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, PARTIAL_FAILED
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dist_company (company_id)
);

-- Solves EV Owner traceability: every payout item records exact batch_id, batch_code, serial_range, source_report_id, kWh
CREATE TABLE IF NOT EXISTS payout_items (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    distribution_id      BIGINT         NOT NULL,
    wallet_id            BIGINT         NOT NULL,
    ev_owner_id          BIGINT         NOT NULL,
    source_report_id     BIGINT         NOT NULL,
    credit_batch_id      BIGINT         NOT NULL,
    batch_code_snap      VARCHAR(128)   NOT NULL,
    serial_range_snap    VARCHAR(128),
    kwh_contributed      DECIMAL(18, 4) NOT NULL,
    allocated_credits    DECIMAL(18, 4) NOT NULL,
    amount_usd           DECIMAL(18, 4) NOT NULL,
    wallet_tx_id         BIGINT,
    claim_token          VARCHAR(64),
    status               VARCHAR(32)    NOT NULL DEFAULT 'PENDING', -- PENDING, PAID, FAILED
    paid_at              TIMESTAMP      NULL,
    INDEX idx_payout_owner (ev_owner_id),
    INDEX idx_payout_batch (credit_batch_id),
    CONSTRAINT fk_payout_dist FOREIGN KEY (distribution_id) REFERENCES profit_distributions (id),
    CONSTRAINT fk_payout_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

-- Shared Infrastructure: Transactional Outbox Pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id             VARCHAR(64)  NOT NULL UNIQUE,
    aggregate_type       VARCHAR(64)  NOT NULL,
    aggregate_id         VARCHAR(128) NOT NULL,
    event_type           VARCHAR(128) NOT NULL,
    payload              JSON         NOT NULL,
    trace_id             VARCHAR(64),
    status               VARCHAR(32)  NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, DEAD_LETTER
    retry_count          INT          NOT NULL DEFAULT 0,
    last_error           TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at         TIMESTAMP    NULL,
    INDEX idx_outbox_status_created (status, created_at)
);

-- Bounded Context 6: Notification
CREATE TABLE IF NOT EXISTS notifications (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id    BIGINT       NOT NULL,
    event_type           VARCHAR(64)  NOT NULL,
    title                VARCHAR(255) NOT NULL,
    body                 TEXT,
    is_read              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_user (recipient_user_id, is_read)
);
