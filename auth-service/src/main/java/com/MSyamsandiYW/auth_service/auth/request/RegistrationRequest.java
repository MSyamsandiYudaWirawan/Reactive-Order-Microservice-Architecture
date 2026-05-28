package com.MSyamsandiYW.auth_service.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RegistrationRequest {
    @NotBlank(message = "VALIDATION.REGISTRATION.NAME.NOT_BLANK")
    @Size(
            min = 5,
            max = 50,
            message = "VALIDATION.REGISTRATION.NAME.SIZE"
    )
    @Pattern(
            regexp = "^[\\p{L} '-]+$",
            message = "VALIDATION.REGISTRATION.NAME.PATTERN"
    )
    @Schema(example = "Syamsandi")
    private String name;

    @NotBlank(message = "VALIDATION.REGISTRATION.EMAIL.NOT_BLANK")
    @Email(message = "VALIDATION.REGISTRATION.EMAIL.FORMAT")
    // @NondisposableEmail(message = "VALIDATION.REGISTRATION.EMAIL.NONDISPOSABLE")
    @Schema(example = "syamsandi@mail.com")
    private String email;

    @NotBlank(message = "VALIDATION.REGISTRATION.PHONE_NUMBER.NOT_BLANK")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "VALIDATION.REGISTRATION.PHONE_NUMBER.FORMAT")
    @Schema(example = "+6281234567890")
    private String phoneNumber;

    @NotBlank(message = "VALIDATION.REGISTRATION.PASSWORD.BLANK")
    @Size(
            min = 8,
            max = 72,
            message = "VALIDATION.REGISTRATION.PASSWORD.SIZE"
    )
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*\\W).*$",
            message = "VALIDATION.REGISTRATION.PASSWORD.WEAK"
    )
    @Schema(example = "pAssword1!_")
    private String password;

    @NotBlank(message = "VALIDATION.REGISTRATION.CONFIRM_PASSWORD.BLANK")
    @Size(
            min = 8,
            max = 72,
            message = "VALIDATION.REGISTRATION.CONFIRM_PASSWORD.SIZE"
    )
    @Schema(example = "pAssword1!_")
    private String confirmPassword;
}
