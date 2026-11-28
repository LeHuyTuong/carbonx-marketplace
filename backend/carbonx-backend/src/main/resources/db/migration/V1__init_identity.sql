-- Bounded Context 1: Identity & Vehicle Registry (no cross-module FKs)

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(191) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(191),
    role            VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS companies (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE,
    business_name   VARCHAR(255) NOT NULL,
    tax_code        VARCHAR(64),
    address         VARCHAR(500),
    kyc_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_companies_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS kyc_profiles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ev_owner_id     BIGINT       NOT NULL UNIQUE,
    document_type   VARCHAR(32)  NOT NULL,
    document_number VARCHAR(64)  NOT NULL,
    phone           VARCHAR(32),
    kyc_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_profiles_user FOREIGN KEY (ev_owner_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS vehicles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ev_owner_id     BIGINT       NOT NULL,
    company_id      BIGINT       NOT NULL,
    plate_number    VARCHAR(32)  NOT NULL UNIQUE,
    brand           VARCHAR(64)  NOT NULL,
    model           VARCHAR(64)  NOT NULL,
    document_url    VARCHAR(500),
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vehicles_company (company_id),
    INDEX idx_vehicles_owner (ev_owner_id),
    CONSTRAINT fk_vehicles_owner FOREIGN KEY (ev_owner_id) REFERENCES users (id),
    CONSTRAINT fk_vehicles_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
