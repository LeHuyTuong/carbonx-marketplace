package com.carbonx.identity.application.exception;

/** Stable application error codes; the REST layer maps them to HTTP status codes. */
public enum IdentityErrorCode {
    EMAIL_ALREADY_USED,
    INVALID_CREDENTIALS,
    ACCOUNT_NOT_ACTIVE,
    NOT_FOUND,
    FORBIDDEN,
    INVALID_OTP,
    ROLE_NOT_SELF_REGISTRABLE,
    COMPANY_NOT_APPROVED,
    VEHICLE_ALREADY_REGISTERED,
    BAD_REQUEST
}
