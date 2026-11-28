package com.carbonx.registry.application.exception;

/** Application-level failure carrying a stable error code for the HTTP layer. */
public class RegistryApplicationException extends RuntimeException {

    private final RegistryErrorCode code;

    public RegistryApplicationException(RegistryErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RegistryApplicationException(RegistryErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public RegistryErrorCode code() {
        return code;
    }

    public static RegistryApplicationException badRequest(String message) {
        return new RegistryApplicationException(RegistryErrorCode.BAD_REQUEST, message);
    }

    public static RegistryApplicationException notFound(String message) {
        return new RegistryApplicationException(RegistryErrorCode.NOT_FOUND, message);
    }

    public static RegistryApplicationException forbidden(String message) {
        return new RegistryApplicationException(RegistryErrorCode.FORBIDDEN, message);
    }

    public static RegistryApplicationException conflict(String message) {
        return new RegistryApplicationException(RegistryErrorCode.CONFLICT, message);
    }
}
