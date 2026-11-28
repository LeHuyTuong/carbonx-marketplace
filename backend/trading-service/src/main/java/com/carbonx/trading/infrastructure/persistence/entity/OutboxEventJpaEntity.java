package com.carbonx.trading.infrastructure.persistence.entity;

import com.carbonx.common.outbox.OutboxMessage;
import com.carbonx.common.outbox.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Transactional outbox row of {@code outbox_events} (PENDING -> SENT | DEAD_LETTER). */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public static OutboxEventJpaEntity pending(String eventId, String aggregateType, String aggregateId,
                                               String eventType, String payload, String traceId) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.eventId = eventId;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.traceId = traceId;
        entity.status = OutboxStatus.PENDING.name();
        entity.retryCount = 0;
        return entity;
    }

    public OutboxMessage toMessage() {
        return new OutboxMessage(eventId, aggregateType, aggregateId, eventType, payload, traceId,
                OutboxStatus.valueOf(status), retryCount, createdAt == null ? Instant.now() : createdAt);
    }

    public void markSent() {
        this.status = OutboxStatus.SENT.name();
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error, int maxRetryCount) {
        this.retryCount = this.retryCount + 1;
        this.lastError = error == null ? null : error.length() <= 1000 ? error : error.substring(0, 1000);
        this.processedAt = Instant.now();
        if (this.retryCount >= maxRetryCount) {
            this.status = OutboxStatus.DEAD_LETTER.name();
        }
    }
}
