package com.carbonx.common.outbox;

import com.carbonx.common.domain.DomainEvent;

/** Transactional Outbox Port: appends DomainEvent inside the service's local DB transaction. */
public interface OutboxPort {
    void append(DomainEvent event);
}
