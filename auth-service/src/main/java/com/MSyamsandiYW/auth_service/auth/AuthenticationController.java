package com.MSyamsandiYW.auth_service.auth;

import com.MSyamsandiYW.auth_service.auth.request.AuthenticationRequest;
import com.MSyamsandiYW.auth_service.auth.request.RefreshRequest;
import com.MSyamsandiYW.auth_service.auth.request.RegistrationRequest;
import com.MSyamsandiYW.auth_service.auth.response.AuthenticationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication API")
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthenticationResponse>> login(
            @Valid
            @RequestBody final AuthenticationRequest request
    ) {
        return authenticationService.login(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Void>> register(
            @Valid
            @RequestBody final RegistrationRequest request
    ) {
        return authenticationService.register(request)
                .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).build()));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthenticationResponse>> refresh(
            @Valid
            @RequestBody
            final RefreshRequest request
            ){
        return authenticationService.refreshToken(request)
                .map(ResponseEntity::ok);
    }

}
