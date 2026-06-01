package com.MSyamsandiYW.auth_service.handler;

import com.MSyamsandiYW.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static com.MSyamsandiYW.common.exception.ErrorCode.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleException(final DisabledException e) {
        log.info("Disabled Exception");
        log.debug(e.getMessage(), e);
        return ResponseEntity.status(ERR_USER_DISABLED.getStatus())
                .body(ErrorResponse.builder()
                        .code(ERR_USER_DISABLED.getCode())
                        .message(ERR_USER_DISABLED.getDefaultMessage())
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleException(final BadCredentialsException e) {
        log.info("Bad Credentials Exception");
        log.debug(e.getMessage(), e);
        return ResponseEntity.status(BAD_CREDENTIALS.getStatus())
                .body(ErrorResponse.builder()
                        .code(BAD_CREDENTIALS.getCode())
                        .message(BAD_CREDENTIALS.getDefaultMessage())
                        .build());
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleException(final UsernameNotFoundException e) {
        log.info("User not found Exception");
        log.debug(e.getMessage(), e);
        return ResponseEntity.status(USERNAME_NOT_FOUND.getStatus())
                .body(ErrorResponse.builder()
                        .code(USERNAME_NOT_FOUND.getCode())
                        .message(USERNAME_NOT_FOUND.getDefaultMessage())
                        .build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleException(final NoSuchElementException e) {
        log.error(e.getMessage(), e);
        return ResponseEntity.status(NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code("TBD")
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
                .stream()
                .forEach(error -> {
                    final String fieldName = ((FieldError) error).getField();
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
