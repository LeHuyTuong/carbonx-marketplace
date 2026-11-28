package com.carbonx.identity.application.exception;

/** Business failure raised by the identity use cases; framework agnostic on purpose. */
public class IdentityApplicationException extends RuntimeException {

    private final IdentityErrorCode code;

    public IdentityApplicationException(IdentityErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public IdentityErrorCode code() {
        return code;
    }

    public static IdentityApplicationException notFound(String resource, Object id) {
        return new IdentityApplicationException(IdentityErrorCode.NOT_FOUND, resource + " not found: " + id);
    }

    public static IdentityApplicationException forbidden(String message) {
        return new IdentityApplicationException(IdentityErrorCode.FORBIDDEN, message);
    }

    public static IdentityApplicationException badRequest(String message) {
        return new IdentityApplicationException(IdentityErrorCode.BAD_REQUEST, message);
    }
}
