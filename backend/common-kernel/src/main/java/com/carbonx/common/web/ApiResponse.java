package com.carbonx.common.web;

import java.time.Instant;

public record ApiResponse<T>(String traceId, Instant timestamp, String code, String message, T data) {
    public static <T> ApiResponse<T> ok(String traceId, T data) {
        return new ApiResponse<>(traceId, Instant.now(), "00", "Success", data);
    }
}
