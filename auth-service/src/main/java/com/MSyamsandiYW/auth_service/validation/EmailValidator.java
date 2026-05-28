package com.MSyamsandiYW.auth_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Set;

public class EmailValidator implements ConstraintValidator<NonDisposableEmail, String> {

    private final Set<String> blocked;

    public EmailValidator(
            @Value("${app.security.disposable-email}") final List<String> domains) {
        this.blocked = Set.copyOf(domains);
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || !email.contains("@")) {
            //already validated by @NotBlank and @Email
            return true;
        }
        final int atIndex = email.lastIndexOf('@');
        final int dotIndex = email.lastIndexOf('.');
        final String localPart = email.substring(0, atIndex);
        final String domain = email.substring(atIndex + 1, dotIndex);
        final String tld = email.substring(dotIndex + 1);

        return !(blocked.contains(domain) || localPart.contains("+"));
    }
}
