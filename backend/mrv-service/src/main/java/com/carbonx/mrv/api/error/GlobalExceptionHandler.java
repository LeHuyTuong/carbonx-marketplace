package com.carbonx.mrv.api.error;

import com.carbonx.mrv.api.dto.ApiError;
import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.exception.MrvErrorCode;
import com.carbonx.mrv.domain.shared.DomainValidationException;
import com.carbonx.mrv.domain.shared.StateTransitionException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

/** Translates application and validation failures into HTTP contracts; never leaks internals. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String FALLBACK_TRACE = "n/a";

    @ExceptionHandler(MrvApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(MrvApplicationException ex, HttpServletRequest request) {
        MrvErrorCode code = ex.code();
        if (code.status().is5xxServerError()) {
            log.error("MRV request failed code={} traceId={}", code, traceId(request));
        }
        return ResponseEntity.status(code.status())
                .body(ApiError.of(traceId(request), code.name(), ex.getMessage()));
    }

    /** A wrong workflow step is a conflict, not a bad payload: the caller must reload the aggregate. */
    @ExceptionHandler(StateTransitionException.class)
    public ResponseEntity<ApiError> handleStateTransition(StateTransitionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(traceId(request), MrvErrorCode.INVALID_STATE.name(), ex.getMessage()));
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiError> handleDomain(DomainValidationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), MrvErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), MrvErrorCode.BAD_REQUEST.name(),
                        "Request validation failed", details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), MrvErrorCode.BAD_REQUEST.name(),
                        "Request body or parameters are not readable"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex,
                                                      HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(traceId(request), MrvErrorCode.BAD_REQUEST.name(),
                        "Multipart part '" + ex.getRequestPartName() + "' is required"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(traceId(request), MrvErrorCode.BAD_REQUEST.name(),
                        "The uploaded file exceeds the configured limit"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(traceId(request), MrvErrorCode.FORBIDDEN.name(),
                        "Access denied for this role"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation traceId={}", traceId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(traceId(request), "CONFLICT", "Resource already exists"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled MRV failure traceId={}", traceId(request), ex);
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
