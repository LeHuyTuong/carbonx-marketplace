package com.carbonx.trading.infrastructure.client;

import com.carbonx.trading.application.exception.TradingApplicationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.RetryableException;

/**
 * Translates deterministic downstream 4xx client/business errors into non-retryable
 * {@link TradingApplicationException} so Resilience4j {@code @Retry} and {@code @CircuitBreaker}
 * act only on transient network or 5xx server faults.
 */
final class FeignErrorTranslator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeignErrorTranslator() {
    }

    static RuntimeException translateClientErrorOrRethrow(FeignException ex) {
        if (ex instanceof RetryableException) {
            return ex;
        }
        int status = ex.status();
        if (status < 400 || status >= 500 || status == 408 || status == 429) {
            return ex;
        }
        String detail = extractMessage(ex);
        return switch (status) {
            case 401, 403 -> TradingApplicationException.forbidden(detail);
            case 404 -> TradingApplicationException.notFound(detail);
            case 409 -> TradingApplicationException.conflict(detail);
            default -> TradingApplicationException.badRequest(detail);
        };
    }

    static TradingApplicationException passthroughBusinessOrUnavailable(String dependency, Throwable failure) {
        if (failure instanceof TradingApplicationException appEx) {
            return appEx;
        }
        return TradingApplicationException.unavailable(dependency, failure);
    }

    private static String extractMessage(FeignException ex) {
        String body = ex.contentUTF8();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = MAPPER.readTree(body);
                for (String field : new String[]{"message", "detail", "error"}) {
                    String value = root.path(field).asText(null);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to Feign exception message when response body is non-JSON.
            }
        }
        String msg = ex.getMessage();
        return msg == null || msg.isBlank()
                ? "Downstream rejected request with status " + ex.status()
                : msg;
    }
}
