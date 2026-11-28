package com.carbonx.identity.api.error;

import com.carbonx.identity.api.dto.ApiError;
import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.exception.IdentityErrorCode;
import com.carbonx.identity.domain.shared.DomainValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/** Translates application and validation failures into HTTP contracts; never leaks internals. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String FALLBACK_TRACE = "n/a";

    @ExceptionHandler(IdentityApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(IdentityApplicationException ex, HttpServletRequest request) {
        IdentityErrorCode code = ex.code();
        HttpStatus status = statusOf(code);
        if (status.is5xxServerError()) {
            log.error("Identity request failed code={} traceId={}", code, traceId(request));
        }
        return ResponseEntity.status(status)
                .body(ApiError.of(traceId(request), code.name(), ex.getMessage()));
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiError> handleDomain(DomainValidationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), IdentityErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), IdentityErrorCode.BAD_REQUEST.name(),
                        "Request validation failed", details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), IdentityErrorCode.BAD_REQUEST.name(),
                        "Request body or parameters are not readable"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(traceId(request), IdentityErrorCode.FORBIDDEN.name(),
                        "Access denied for this role"));
    }

    /** Unique-constraint races (email, plate number) map to a clean conflict instead of a 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation traceId={}", traceId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(traceId(request), "CONFLICT", "Resource already exists"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled identity failure traceId={}", traceId(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(traceId(request), "INTERNAL_ERROR", "Unexpected server error"));
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + (error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
    }

    private static HttpStatus statusOf(IdentityErrorCode code) {
        return switch (code) {
            case EMAIL_ALREADY_USED, VEHICLE_ALREADY_REGISTERED, COMPANY_NOT_APPROVED -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, ACCOUNT_NOT_ACTIVE -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_OTP, ROLE_NOT_SELF_REGISTRABLE, BAD_REQUEST -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String traceId(HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String header = request == null ? null : request.getHeader("X-Request-Trace");
        return header == null || header.isBlank() ? FALLBACK_TRACE : header;
    }
}
