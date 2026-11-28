CREATE TABLE IF NOT EXISTS wallets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    tx_type VARCHAR(32) NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    balance_after DECIMAL(18, 4) NOT NULL,
    order_ref BIGINT NULL,
    idempotency_key VARCHAR(128) NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_wallet_tx_idempotency UNIQUE (idempotency_key),
    INDEX idx_wallet_tx_user_id (user_id),
    INDEX idx_wallet_tx_order_ref (order_ref)
);

CREATE TABLE IF NOT EXISTS payment_details (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    account_holder VARCHAR(128) NOT NULL,
    branch VARCHAR(128) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_payment_details_user_id UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS withdrawals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    bank_name VARCHAR(128) NULL,
    account_number VARCHAR(64) NULL,
    account_holder VARCHAR(128) NULL,
    processed_by BIGINT NULL,
    processed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_withdrawals_user_id (user_id),
    INDEX idx_withdrawals_status (status)
);

CREATE TABLE IF NOT EXISTS profit_distributions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    source_report_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    total_amount DECIMAL(18, 4) NOT NULL,
    recipient_count INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_profit_dist_idempotency UNIQUE (idempotency_key),
    INDEX idx_profit_dist_company_id (company_id)
);
