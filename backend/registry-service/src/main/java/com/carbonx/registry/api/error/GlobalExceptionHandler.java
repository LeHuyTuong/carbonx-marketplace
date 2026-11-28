package com.carbonx.registry.api.error;

import com.carbonx.registry.api.dto.ApiError;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.exception.RegistryErrorCode;
import com.carbonx.registry.domain.shared.DomainValidationException;
import com.carbonx.registry.domain.shared.InsufficientCreditsException;
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

    @ExceptionHandler(RegistryApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(RegistryApplicationException ex, HttpServletRequest request) {
        RegistryErrorCode code = ex.code();
        if (code.status().is5xxServerError()) {
            log.error("Registry request failed code={} traceId={}", code, traceId(request));
        }
        return ResponseEntity.status(code.status())
                .body(ApiError.of(traceId(request), code.name(), ex.getMessage()));
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiError> handleDomain(DomainValidationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), RegistryErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }

    /** Overdrawing a holding is a state conflict, not a malformed request. */
    @ExceptionHandler(InsufficientCreditsException.class)
    public ResponseEntity<ApiError> handleInsufficient(InsufficientCreditsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(traceId(request), RegistryErrorCode.INSUFFICIENT_CREDITS.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), RegistryErrorCode.BAD_REQUEST.name(),
                        "Request validation failed", details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), RegistryErrorCode.BAD_REQUEST.name(),
                        "Request body or parameters are not readable"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(traceId(request), RegistryErrorCode.FORBIDDEN.name(),
                        "Access denied for this role"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation traceId={}", traceId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(traceId(request), RegistryErrorCode.CONFLICT.name(), "Resource already exists"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled registry failure traceId={}", traceId(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(traceId(request), "INTERNAL_ERROR", "Unexpected server error"));
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + (error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
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
