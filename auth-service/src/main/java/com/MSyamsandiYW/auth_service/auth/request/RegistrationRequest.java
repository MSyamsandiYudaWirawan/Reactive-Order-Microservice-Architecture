package com.MSyamsandiYW.auth_service.auth.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RegistrationRequest {
    private String name;
    private String email;
    private String password;
    private String confirmPassword;
    private String phoneNumber;
}
