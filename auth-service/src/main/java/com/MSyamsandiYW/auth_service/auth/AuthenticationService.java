package com.MSyamsandiYW.auth_service.auth;

import com.MSyamsandiYW.auth_service.auth.request.AuthenticationRequest;
import com.MSyamsandiYW.auth_service.auth.request.RefreshRequest;
import com.MSyamsandiYW.auth_service.auth.request.RegistrationRequest;
import com.MSyamsandiYW.auth_service.auth.response.AuthenticationResponse;
import reactor.core.publisher.Mono;

public interface AuthenticationService {
    Mono<AuthenticationResponse> login(AuthenticationRequest request);
    Mono<Void> register(RegistrationRequest request);
    Mono<AuthenticationResponse> refreshToken(RefreshRequest request);
}
