package com.carbonx.registry.domain.credit;

/** Append-only ledger entry types written to {@code credit_movements}. */
public enum MovementType {
    /** Serial range handed to the issuer when a batch was minted. */
    ISSUE,
    /** Credits reserved for one marketplace listing; still owned by the seller. */
    LOCK,
    /** Reservation undone, either by the seller or by an Order Saga compensation. */
    RELEASE,
    /** Credits leaving the seller holding for a buyer holding. */
    TRANSFER_OUT,
    /** Credits arriving in the buyer holding. */
    TRANSFER_IN,
    /** Credits permanently cancelled (retired) with a retirement certificate. */
    RETIRE
}
