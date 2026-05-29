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
public class ProfileUpdateRequest {

    @NotBlank(message = "VALIDATION.PROFILE_UPDATE.NAME.NOT_BLANK")
    @Size(  min = 5,
            max = 50,
            message = "VALIDATION.PROFILE_UPDATE.NAME.SIZE"
    )
    @Pattern(
            regexp = "^[\\p{L} '-]+$",
            message = "VALIDATION.PROFILE_UPDATE.NAME.PATTERN"
    )
    @Schema(example = "Syamsandi")
    private String name;
}
