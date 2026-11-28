-- Forward-only Flyway V2: Persistent 6-state machine for Saga Orchestration + audit trail (Saga + Outbox Pattern)
CREATE TABLE IF NOT EXISTS order_saga_instances (
    saga_id         VARCHAR(64)  PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    order_id        BIGINT       NULL,
    listing_id      BIGINT       NOT NULL,
    saga_state      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    current_step    VARCHAR(64)  NOT NULL DEFAULT 'RESERVE_LISTING',
    retry_count     INT          NOT NULL DEFAULT 0,
    timeout_at      TIMESTAMP    NOT NULL,
    last_error      TEXT,
    trace_id        VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_saga_state_timeout (saga_state, timeout_at),
    CONSTRAINT chk_saga_state_valid CHECK (
        saga_state IN ('PENDING', 'EXECUTING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED')
    )
);

CREATE TABLE IF NOT EXISTS order_saga_transition_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    saga_id     VARCHAR(64) NOT NULL,
    from_state  VARCHAR(32) NOT NULL,
    to_state    VARCHAR(32) NOT NULL,
    step_name   VARCHAR(64) NOT NULL,
    detail      TEXT,
    occurred_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_log_instance FOREIGN KEY (saga_id) REFERENCES order_saga_instances (saga_id)
);
