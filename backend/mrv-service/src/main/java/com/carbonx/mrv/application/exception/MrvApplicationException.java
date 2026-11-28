package com.carbonx.mrv.application.exception;

/** Application-level failure carrying a stable error code for the HTTP layer. */
public class MrvApplicationException extends RuntimeException {

    private final MrvErrorCode code;

    public MrvApplicationException(MrvErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public MrvApplicationException(MrvErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public MrvErrorCode code() {
        return code;
    }

    public static MrvApplicationException badRequest(String message) {
        return new MrvApplicationException(MrvErrorCode.BAD_REQUEST, message);
    }

    public static MrvApplicationException notFound(String message) {
        return new MrvApplicationException(MrvErrorCode.NOT_FOUND, message);
    }

    public static MrvApplicationException forbidden(String message) {
        return new MrvApplicationException(MrvErrorCode.FORBIDDEN, message);
    }

    public static MrvApplicationException unavailable(String dependency, Throwable cause) {
        return new MrvApplicationException(MrvErrorCode.SERVICE_UNAVAILABLE,
                dependency + " is unavailable; the request cannot be completed safely", cause);
    }
}
