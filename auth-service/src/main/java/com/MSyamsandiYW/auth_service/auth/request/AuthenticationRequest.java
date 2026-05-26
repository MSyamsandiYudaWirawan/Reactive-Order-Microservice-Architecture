package com.MSyamsandiYW.auth_service.auth.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AuthenticationRequest {

    private String email;
    private String password;
}
