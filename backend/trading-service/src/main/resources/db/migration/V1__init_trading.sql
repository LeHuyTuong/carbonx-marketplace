CREATE TABLE IF NOT EXISTS market_listings (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    holding_id         BIGINT         NOT NULL,
    batch_id           BIGINT         NOT NULL,
    batch_code_snap    VARCHAR(128)   NOT NULL,
    project_title_snap VARCHAR(255)   NOT NULL,
    seller_company_id  BIGINT         NOT NULL,
    total_qty          BIGINT         NOT NULL,
    available_qty      BIGINT         NOT NULL,
    price_per_credit   DECIMAL(18, 4) NOT NULL,
    status             VARCHAR(32)    NOT NULL DEFAULT 'LISTED',
    expires_at         TIMESTAMP      NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS listing_reservations (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id       BIGINT       NOT NULL,
    buyer_company_id BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    quantity         BIGINT       NOT NULL,
    saga_state       VARCHAR(32)  NOT NULL DEFAULT 'RESERVED',
    expires_at       TIMESTAMP    NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_res_listing FOREIGN KEY (listing_id) REFERENCES market_listings (id)
);

CREATE TABLE IF NOT EXISTS trade_orders (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code        VARCHAR(64)    NOT NULL UNIQUE,
    idempotency_key   VARCHAR(128)   NOT NULL UNIQUE,
    listing_id        BIGINT         NOT NULL,
    batch_id          BIGINT         NOT NULL,
    buyer_company_id  BIGINT         NOT NULL,
    seller_company_id BIGINT         NOT NULL,
    quantity          BIGINT         NOT NULL,
    unit_price        DECIMAL(18, 4) NOT NULL,
    gross_amount      DECIMAL(18, 4) NOT NULL,
    platform_fee      DECIMAL(18, 4) NOT NULL,
    net_seller_amount DECIMAL(18, 4) NOT NULL,
    status            VARCHAR(32)    NOT NULL DEFAULT 'FINISHED',
    settled_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES market_listings (id)
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
