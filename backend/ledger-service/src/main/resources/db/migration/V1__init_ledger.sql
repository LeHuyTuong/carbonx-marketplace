CREATE TABLE IF NOT EXISTS wallets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT         NOT NULL UNIQUE,
    balance_usd   DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallets_non_negative CHECK (balance_usd >= 0)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id       BIGINT         NOT NULL,
    idempotency_key VARCHAR(128)   NOT NULL UNIQUE,
    tx_type         VARCHAR(32)    NOT NULL,
    amount_usd      DECIMAL(18, 4) NOT NULL,
    balance_before  DECIMAL(18, 4) NOT NULL,
    balance_after   DECIMAL(18, 4) NOT NULL,
    reference_type  VARCHAR(64),
    reference_id    VARCHAR(128),
    description     VARCHAR(500),
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wtx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE TABLE IF NOT EXISTS bank_accounts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id      BIGINT       NOT NULL,
    bank_name      VARCHAR(128) NOT NULL,
    branch         VARCHAR(128),
    account_number VARCHAR(64)  NOT NULL,
    account_holder VARCHAR(191) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE TABLE IF NOT EXISTS withdrawals (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id          BIGINT         NOT NULL,
    bank_account_id    BIGINT         NOT NULL,
    amount_usd         DECIMAL(18, 4) NOT NULL,
    status             VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    processed_by_admin BIGINT,
    requested_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at       TIMESTAMP      NULL,
    CONSTRAINT fk_wd_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wd_bank FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id)
);

CREATE TABLE IF NOT EXISTS profit_distributions (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id            BIGINT         NOT NULL,
    source_report_id      BIGINT         NOT NULL UNIQUE,
    credit_batch_id       BIGINT         NOT NULL,
    batch_code_snap       VARCHAR(128)   NOT NULL,
    period                VARCHAR(32)    NOT NULL,
    unit_price_per_credit DECIMAL(18, 4) NOT NULL,
    total_payout_usd      DECIMAL(18, 4) NOT NULL,
    status                VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payout_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    distribution_id   BIGINT         NOT NULL,
    wallet_id         BIGINT         NOT NULL,
    ev_owner_id       BIGINT         NOT NULL,
    source_report_id  BIGINT         NOT NULL,
    credit_batch_id   BIGINT         NOT NULL,
    batch_code_snap   VARCHAR(128)   NOT NULL,
    serial_range_snap VARCHAR(128),
    kwh_contributed   DECIMAL(18, 4) NOT NULL,
    allocated_credits DECIMAL(18, 4) NOT NULL,
    amount_usd        DECIMAL(18, 4) NOT NULL,
    wallet_tx_id      BIGINT,
    claim_token       VARCHAR(64),
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    paid_at           TIMESTAMP      NULL,
    CONSTRAINT fk_payout_dist FOREIGN KEY (distribution_id) REFERENCES profit_distributions (id),
    CONSTRAINT fk_payout_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL UNIQUE,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSON         NOT NULL,
    trace_id        VARCHAR(64),
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at    TIMESTAMP    NULL,
    INDEX idx_outbox_status_created (status, created_at)
);
