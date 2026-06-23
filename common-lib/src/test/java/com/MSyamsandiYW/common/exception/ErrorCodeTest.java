package com.MSyamsandiYW.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("ErrorCode - should have correct HTTP status mapping")
    void errorCode_httpStatusMapping() {
        assertThat(ErrorCode.BAD_CREDENTIALS.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.EMAIL_ALREADY_EXISTS.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.USER_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.OUT_OF_STOCK.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.USER_FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.INTERNAL_EXCEPTION.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("ErrorCode - should have correct code strings")
    void errorCode_codeStrings() {
        assertThat(ErrorCode.PAYMENT_NOT_FOUND.getCode()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(ErrorCode.TRANSACTION_NOT_FOUND.getCode()).isEqualTo("TRANSACTION_NOT_FOUND");
        assertThat(ErrorCode.DUPLICATE_REQUEST.getCode()).isEqualTo("DUPLICATE_REQUEST");
    }

    @Test
    @DisplayName("ErrorCode - fromCode should resolve existing code")
    void fromCode_existingCode() {
        ErrorCode result = ErrorCode.fromCode("BAD_CREDENTIALS");
        assertThat(result).isEqualTo(ErrorCode.BAD_CREDENTIALS);
    }

    @Test
    @DisplayName("ErrorCode - fromCode should return INTERNAL_EXCEPTION for unknown code")
    void fromCode_unknownCode() {
        ErrorCode result = ErrorCode.fromCode("NON_EXISTENT_CODE");
        assertThat(result).isEqualTo(ErrorCode.INTERNAL_EXCEPTION);
    }

    @Test
    @DisplayName("BusinessException - should format message with args")
    void businessException_formattedMessage() {
        BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("User not found");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("BusinessException - should use default message when no args")
    void businessException_defaultMessage() {
        BusinessException ex = new BusinessException(ErrorCode.OUT_OF_STOCK);
        assertThat(ex.getMessage()).isEqualTo("Insufficient stock to fulfill the order");
        assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
