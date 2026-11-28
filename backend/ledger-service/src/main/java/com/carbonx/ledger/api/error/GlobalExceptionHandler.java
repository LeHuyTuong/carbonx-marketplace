package com.carbonx.ledger.api.error;

import com.carbonx.ledger.application.exception.LedgerApplicationException;
import com.carbonx.ledger.domain.shared.LedgerDomainException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public record ApiError(String code, String message, String path, Instant timestamp) {}

    @ExceptionHandler(LedgerApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(LedgerApplicationException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.code().httpStatus())
                .body(new ApiError(ex.code().name(), ex.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(LedgerDomainException.class)
    public ResponseEntity<ApiError> handleDomain(LedgerDomainException ex, HttpServletRequest request) {
        String localized = messageSource.getMessage(
                ex.messageKey(), ex.args(), ex.getMessage(), LocaleContextHolder.getLocale());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(ex.errorCode().code(), localized, request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", details, request.getRequestURI(), Instant.now()));
    }
}
