package com.MSyamsandiYW.auth_service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailValidator.class)
public @interface NonDisposableEmail {
    String message() default "Disposable email addresses are not allowed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
