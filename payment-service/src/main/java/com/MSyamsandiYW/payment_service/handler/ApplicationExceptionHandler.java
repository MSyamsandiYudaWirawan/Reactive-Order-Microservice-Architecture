package com.MSyamsandiYW.payment_service.handler;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorResponse;
import com.MSyamsandiYW.common.exception.ValidationError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.ArrayList;
import java.util.List;

import static com.MSyamsandiYW.common.exception.ErrorCode.INTERNAL_EXCEPTION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestControllerAdvice
@Slf4j
public class ApplicationExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleException(final BusinessException e) {
        log.info("Business Exception");
        log.debug(e.getMessage(), e);
        return ResponseEntity.status(
                        e.getErrorCode().getStatus() != null ? e.getErrorCode().getStatus() : BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code(e.getErrorCode().getCode())
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleException(final WebExchangeBindException e) {
        log.info("Validation Exception");
        log.debug(e.getMessage(), e);
        final List<ValidationError> errors = new ArrayList<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> {
                    final String fieldName = error.getField();
                    final String errorCode = error.getDefaultMessage();
                    errors.add(ValidationError.builder()
                            .field(fieldName)
                            .code(errorCode)
                            .message(errorCode)
                            .build());
                });
        return ResponseEntity.status(BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("VALIDATION_ERROR")
                        .message("Validation error occurred")
                        .validationErrors(errors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(final Exception e) {
        log.error(e.getMessage(), e);
        return ResponseEntity.status(INTERNAL_EXCEPTION.getStatus())
                .body(ErrorResponse.builder()
                        .code(INTERNAL_EXCEPTION.getCode())
                        .message(INTERNAL_EXCEPTION.getDefaultMessage())
                        .build());
    }
}