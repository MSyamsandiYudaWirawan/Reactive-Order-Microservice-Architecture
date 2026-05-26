package com.MSyamsandiYW.auth_service.auth.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RefreshRequest {

    private String refreshToken;
}
