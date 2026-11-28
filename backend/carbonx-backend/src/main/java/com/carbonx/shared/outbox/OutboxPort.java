package com.carbonx.shared.outbox;

import com.carbonx.shared.domain.DomainEvent;

/** Outbound Port: persists a DomainEvent into outbox_events within the caller's DB transaction. */
public interface OutboxPort {
    void append(DomainEvent event);
}
