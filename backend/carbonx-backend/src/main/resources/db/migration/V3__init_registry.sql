-- Bounded Context 3: Carbon Registry (Credit Batches -> Credit Holdings -> Credit Movements & Retirement Certificates)
-- Solves 100 issued -> 80 sold to Buyer Holding + 20 retained in Seller Holding with full serial provenance.

CREATE TABLE IF NOT EXISTS credit_batches (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_code           VARCHAR(128)   NOT NULL UNIQUE,
    source_report_id     BIGINT         NOT NULL UNIQUE,
    project_id           BIGINT         NOT NULL,
    project_title_snap   VARCHAR(255)   NOT NULL,
    issuer_company_id    BIGINT         NOT NULL,
    vintage_year         INT            NOT NULL,
    total_issued_qty     BIGINT         NOT NULL,
    total_tco2e          DECIMAL(18, 4) NOT NULL,
    residual_tco2e       DECIMAL(18, 4) NOT NULL DEFAULT 0,
    serial_prefix        VARCHAR(64)    NOT NULL,
    serial_start         BIGINT         NOT NULL,
    serial_end           BIGINT         NOT NULL,
    issued_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at           TIMESTAMP      NULL,
    INDEX idx_batches_company (issuer_company_id)
);

CREATE TABLE IF NOT EXISTS credit_holdings (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id             BIGINT       NOT NULL,
    holder_company_id    BIGINT       NOT NULL,
    available_qty        BIGINT       NOT NULL DEFAULT 0,
    locked_qty           BIGINT       NOT NULL DEFAULT 0,
    retired_qty          BIGINT       NOT NULL DEFAULT 0,
    serial_start         BIGINT       NOT NULL,
    serial_end           BIGINT       NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_holdings_holder (holder_company_id),
    INDEX idx_holdings_batch (batch_id),
    CONSTRAINT fk_holdings_batch FOREIGN KEY (batch_id) REFERENCES credit_batches (id),
    CONSTRAINT chk_holdings_non_negative CHECK (available_qty >= 0 AND locked_qty >= 0 AND retired_qty >= 0)
);

CREATE TABLE IF NOT EXISTS credit_movements (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    holding_id           BIGINT       NOT NULL,
    batch_id             BIGINT       NOT NULL,
    movement_type        VARCHAR(32)  NOT NULL, -- ISSUE, LOCK, UNLOCK, TRANSFER_OUT, TRANSFER_IN, RETIRE
    quantity             BIGINT       NOT NULL,
    serial_start         BIGINT       NOT NULL,
    serial_end           BIGINT       NOT NULL,
    reference_id         VARCHAR(128),          -- orderId, listingId, retirementCertId
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_movements_holding (holding_id),
    INDEX idx_movements_batch (batch_id),
    CONSTRAINT fk_movements_holding FOREIGN KEY (holding_id) REFERENCES credit_holdings (id)
);

CREATE TABLE IF NOT EXISTS retirement_certificates (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    certificate_code     VARCHAR(128) NOT NULL UNIQUE,
    holding_id           BIGINT       NOT NULL,
    batch_id             BIGINT       NOT NULL,
    holder_company_id    BIGINT       NOT NULL,
    retired_qty          BIGINT       NOT NULL,
    serial_start         BIGINT       NOT NULL,
    serial_end           BIGINT       NOT NULL,
    beneficiary_name     VARCHAR(255),
    reason               VARCHAR(500),
    pdf_url              VARCHAR(500),
    retired_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_retire_holding FOREIGN KEY (holding_id) REFERENCES credit_holdings (id)
);
