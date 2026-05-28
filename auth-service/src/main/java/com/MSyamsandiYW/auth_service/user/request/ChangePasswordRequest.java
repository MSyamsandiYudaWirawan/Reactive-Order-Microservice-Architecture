package com.MSyamsandiYW.auth_service.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "VALIDATION.CHANGE_PASSWORD.CURRENT_PASSWORD.NOT_BLANK")
    @Size(
            min = 8,
            max = 72,
            message = "VALIDATION.CHANGE_PASSWORD.CURRENT_PASSWORD.SIZE"
    )
    @Schema(example = "pAssword1!_")
    private String currentPassword;

    @NotBlank(message = "VALIDATION.CHANGE_PASSWORD.NEW_PASSWORD.NOT_BLANK")
    @Size(
            min = 8,
            max = 72,
            message = "VALIDATION.CHANGE_PASSWORD.NEW_PASSWORD.SIZE"
    )
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*\\W).*$",
            message = "VALIDATION.CHANGE_PASSWORD.NEW_PASSWORD.WEAK"
    )
    @Schema(example = "pAssword1!_")
    private String newPassword;

    @NotBlank(message = "VALIDATION.CHANGE_PASSWORD.CONFIRM_NEW_PASSWORD.NOT_BLANK")
    @Size(
            min = 8,
            max = 72,
            message = "VALIDATION.CHANGE_PASSWORD.CONFIRM_NEW_PASSWORD.SIZE"
    )
    @Schema(example = "pAssword1!_")
    private String confirmNewPassword;
}
