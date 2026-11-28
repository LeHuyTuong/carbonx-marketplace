CREATE TABLE IF NOT EXISTS projects (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(255)   NOT NULL,
    description      TEXT,
    emission_factor  DECIMAL(12, 6) NOT NULL DEFAULT 0.676600,
    buffer_rate      DECIMAL(6, 4)  NOT NULL DEFAULT 0.0500,
    uncertainty_rate DECIMAL(6, 4)  NOT NULL DEFAULT 0.0300,
    leakage_rate     DECIMAL(6, 4)  NOT NULL DEFAULT 0.0200,
    status           VARCHAR(32)    NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_applications (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT        NOT NULL,
    company_id        BIGINT        NOT NULL,
    company_name_snap VARCHAR(255),
    document_url      VARCHAR(500),
    status            VARCHAR(32)   NOT NULL DEFAULT 'SUBMITTED',
    cva_reviewer_id   BIGINT,
    cva_note          TEXT,
    admin_reviewer_id BIGINT,
    admin_note        TEXT,
    submitted_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_proj_app_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE TABLE IF NOT EXISTS emission_reports (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id    BIGINT         NOT NULL,
    project_id        BIGINT         NOT NULL,
    company_id        BIGINT         NOT NULL,
    period            VARCHAR(32)    NOT NULL,
    total_energy_kwh  DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_co2_kg      DECIMAL(18, 4) NOT NULL DEFAULT 0,
    vehicle_count     INT            NOT NULL DEFAULT 0,
    file_sha256       VARCHAR(64),
    original_filename VARCHAR(255),
    status            VARCHAR(32)    NOT NULL DEFAULT 'SUBMITTED',
    verifier_id       BIGINT,
    ai_pre_score      DECIMAL(5, 2),
    ai_version        VARCHAR(64),
    ai_pre_notes      TEXT,
    submitted_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reports_app FOREIGN KEY (application_id) REFERENCES project_applications (id)
);

CREATE TABLE IF NOT EXISTS fraud_analyses (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id  BIGINT       NOT NULL,
    rule_code  VARCHAR(64)  NOT NULL,
    rule_name  VARCHAR(255) NOT NULL,
    score      INT          NOT NULL,
    max_score  INT          NOT NULL,
    passed     BOOLEAN      NOT NULL DEFAULT TRUE,
    evidence   TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fraud_report FOREIGN KEY (report_id) REFERENCES emission_reports (id)
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
