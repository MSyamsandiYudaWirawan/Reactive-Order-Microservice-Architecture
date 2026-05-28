package com.MSyamsandiYW.auth_service.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RefreshRequest {

    @NotBlank(message = "VALIDATION.REFRESH_TOKEN.NOT_BLANK")
    @Schema(example = "<REFRESH_TOKEN>")
    private String refreshToken;
}
