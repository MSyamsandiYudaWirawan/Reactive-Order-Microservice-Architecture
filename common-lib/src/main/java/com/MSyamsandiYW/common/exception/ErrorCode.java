package com.MSyamsandiYW.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@Getter
public enum ErrorCode {

    // ===== Authentication & Credentials =====
    BAD_CREDENTIALS("BAD_CREDENTIALS", "Username and / or password is incorrect", UNAUTHORIZED),
    ERR_USER_DISABLED("ERR_USER_DISABLED", "User account is disabled, please activate your account or contact the administrator", FORBIDDEN),
    INVALID_CURRENT_PASSWORD("INVALID_CURRENT_PASSWORD", "The current password is incorrect", BAD_REQUEST),
    PASSWORD_MISMATCH("ERR_PASSWORD_MISMATCH", "The password and confirmation do not match", BAD_REQUEST),
    CHANGE_PASSWORD_MISMATCH("ERR_PASSWORD_MISMATCH", "New password and confirmation do not match", BAD_REQUEST),
    PASSWORD_EXPIRED("PASSWORD_EXPIRED", "The current password is expired", UNAUTHORIZED),

    // ===== Token / JWT =====
    INVALID_TOKEN("INVALID_TOKEN", "Token is invalid or malformed", UNAUTHORIZED),
    INVALID_TYPE_TOKEN("INVALID_TYPE_TOKEN", "Token Type is invalid", UNAUTHORIZED),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token has expired", UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", "Refresh Token has expired", UNAUTHORIZED),
    TOKEN_SIGNATURE_INVALID("TOKEN_SIGNATURE_INVALID", "Token signature verification failed", UNAUTHORIZED),

    // ===== User Account =====
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", NOT_FOUND),
    USERNAME_NOT_FOUND("USERNAME_NOT_FOUND", "Cannot find user with the provided username", NOT_FOUND),
    EMAIL_ALREADY_EXISTS("ERR_EMAIL_EXISTS", "Email already exists", CONFLICT),
    PHONE_ALREADY_EXISTS("ERR_PHONE_EXISTS", "An account with this phone number already exists", CONFLICT),
    ACCOUNT_ALREADY_DEACTIVATED("ACCOUNT_ALREADY_DEACTIVATED", "Account has been deactivated", CONFLICT),
    ACCOUNT_ALREADY_ACTIVATED("ACCOUNT_ALREADY_ACTIVATED", "Account has been activated", CONFLICT),
    ACCOUNT_ALREADY_DELETED("ACCOUNT_ALREADY_DELETED", "Account has been deleted", GONE),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Account has been locked", FORBIDDEN),
    ERR_SENDING_ACTIVATION_EMAIL("ERR_SENDING_ACTIVATION_EMAIL", "An error occurred while sending the activation email", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== Authorization =====
    USER_UNAUTHORIZED("USER_UNAUTHORIZED", "User is unauthorized to perform this action", UNAUTHORIZED),
    USER_FORBIDDEN("USER_FORBIDDEN", "User does not have permission to perform this action", FORBIDDEN),

    // ===== Order =====
    ORDER_PENDING("ORDER_PENDING", "Order is still pending, try again later", CONFLICT),
    ORDER_ALREADY_PAID("ORDER_ALREADY_PAID", "Order is already paid", CONFLICT),
    ORDER_ALREADY_COMPLETED("ORDER_ALREADY_COMPLETED", "Order is already completed", CONFLICT),
    ORDER_ALREADY_FAILED("ORDER_ALREADY_FAILED", "Order is already failed", CONFLICT),
    ORDER_ALREADY_REFUNDED("ORDER_ALREADY_REFUNDED", "Order is already refunded", CONFLICT),
    ORDER_OUT_OF_STOCK("ORDER_OUT_OF_STOCK", "Order is out of stock", UNPROCESSABLE_ENTITY),
    ORDER_ALREADY_EXPIRED("ORDER_ALREADY_EXPIRED", "Order is already expired", GONE),

    // ===== Payment =====
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "Payment not found", NOT_FOUND),
    INVALID_PAYMENT_METHOD("INVALID_PAYMENT_METHOD", "Payment method is invalid", BAD_REQUEST),
    PAYMENT_ALREADY_PENDING("PAYMENT_ALREADY_PENDING", "Payment is already pending", CONFLICT),

    // ===== Inventory =====
    OUT_OF_STOCK("OUT_OF_STOCK", "Insufficient stock to fulfill the order", UNPROCESSABLE_ENTITY),
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "Product not found", NOT_FOUND),

    // ===== Transaction & Idempotency =====
    TRANSACTION_NOT_FOUND("TRANSACTION_NOT_FOUND", "Transaction not found", NOT_FOUND),
    DUPLICATE_REQUEST("DUPLICATE_REQUEST", "Request has already been processed", CONFLICT),
    CATEGORY_ALREADY_EXISTS_FOR_USER("CATEGORY_ALREADY_EXISTS_FOR_USER", "Category already exists for this user", CONFLICT),


    // ===== Gateway =====
    MISSING_IDEMPOTENCY_KEY("MISSING_IDEMPOTENCY_KEY", "X-Idempotency-Key header is required", BAD_REQUEST),

    // ===== Service Availability =====
    INVENTORY_SERVICE_UNAVAILABLE("INVENTORY_SERVICE_UNAVAILABLE", "Inventory service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    ORDER_SERVICE_UNAVAILABLE("ORDER_SERVICE_UNAVAILABLE", "Order service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_EXCEPTION("INTERNAL_EXCEPTION", "An internal exception occurred, please try again or contact the admin", HttpStatus.INTERNAL_SERVER_ERROR),

    ;

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(final String code,
              final String defaultMessage,
              final HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return INTERNAL_EXCEPTION;
    }
}
