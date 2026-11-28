-- Bounded Context 4: Trading (Market Listings, Listing Reservations, 1-step Instant Trade Orders)

CREATE TABLE IF NOT EXISTS market_listings (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    holding_id           BIGINT         NOT NULL,
    batch_id             BIGINT         NOT NULL,
    batch_code_snap      VARCHAR(128)   NOT NULL,
    project_title_snap   VARCHAR(255)   NOT NULL,
    seller_company_id    BIGINT         NOT NULL,
    total_qty            BIGINT         NOT NULL,
    available_qty        BIGINT         NOT NULL,
    price_per_credit     DECIMAL(18, 4) NOT NULL,
    status               VARCHAR(32)    NOT NULL DEFAULT 'LISTED', -- LISTED, PARTIALLY_SOLD, SOLD, CANCELLED
    expires_at           TIMESTAMP      NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_listings_seller (seller_company_id),
    INDEX idx_listings_status (status),
    CONSTRAINT chk_listings_qty CHECK (available_qty >= 0 AND available_qty <= total_qty)
);

CREATE TABLE IF NOT EXISTS listing_reservations (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id           BIGINT       NOT NULL,
    buyer_company_id     BIGINT       NOT NULL,
    idempotency_key      VARCHAR(128) NOT NULL UNIQUE,
    quantity             BIGINT       NOT NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'RESERVED', -- RESERVED, COMMITTED, RELEASED
    expires_at           TIMESTAMP    NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_listing FOREIGN KEY (listing_id) REFERENCES market_listings (id)
);

CREATE TABLE IF NOT EXISTS trade_orders (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code           VARCHAR(64)    NOT NULL UNIQUE,
    idempotency_key      VARCHAR(128)   NOT NULL UNIQUE,
    listing_id           BIGINT         NOT NULL,
    batch_id             BIGINT         NOT NULL,
    buyer_company_id     BIGINT         NOT NULL,
    seller_company_id    BIGINT         NOT NULL,
    quantity             BIGINT         NOT NULL,
    unit_price           DECIMAL(18, 4) NOT NULL,
    gross_amount         DECIMAL(18, 4) NOT NULL,
    platform_fee         DECIMAL(18, 4) NOT NULL, -- 5%
    net_seller_amount    DECIMAL(18, 4) NOT NULL,
    status               VARCHAR(32)    NOT NULL DEFAULT 'FINISHED', -- 1-step instant wallet settlement
    settled_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_orders_buyer (buyer_company_id),
    INDEX idx_orders_seller (seller_company_id),
    CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES market_listings (id)
);
