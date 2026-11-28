package com.carbonx.mrv.api.dto;

import java.time.Instant;
import java.util.List;

/** Error envelope carrying the correlation trace id required by the observability rules. */
public record ApiError(String traceId, Instant timestamp, String code, String message, List<String> details) {

    public static ApiError of(String traceId, String code, String message) {
        return new ApiError(traceId, Instant.now(), code, message, List.of());
    }

    public static ApiError of(String traceId, String code, String message, List<String> details) {
        return new ApiError(traceId, Instant.now(), code, message, List.copyOf(details));
    }
}
