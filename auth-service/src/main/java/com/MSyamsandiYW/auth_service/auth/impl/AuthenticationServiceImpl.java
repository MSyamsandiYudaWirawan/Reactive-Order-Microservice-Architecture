package com.MSyamsandiYW.auth_service.auth.impl;

import com.MSyamsandiYW.auth_service.auth.AuthenticationService;
import com.MSyamsandiYW.auth_service.auth.request.AuthenticationRequest;
import com.MSyamsandiYW.auth_service.auth.request.RefreshRequest;
import com.MSyamsandiYW.auth_service.auth.request.RegistrationRequest;
import com.MSyamsandiYW.auth_service.auth.response.AuthenticationResponse;
import com.MSyamsandiYW.auth_service.exception.BusinessException;
import com.MSyamsandiYW.auth_service.exception.ErrorCode;
import com.MSyamsandiYW.auth_service.security.JwtService;
import com.MSyamsandiYW.auth_service.user.User;
import com.MSyamsandiYW.auth_service.user.UserMapper;
import com.MSyamsandiYW.auth_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public Mono<AuthenticationResponse> login(AuthenticationRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        final String tokenType = "Bearer";

        return authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getEmail(),
                                request.getPassword()
                        )
                )
                .map(auth -> (User) Objects.requireNonNull(auth.getPrincipal()))
                .flatMap(user -> Mono.zip(jwtService.generateAccessToken(user.getEmail()),
                        jwtService.generateRefreshToken(user.getEmail())))
                .map(tuple ->
                        AuthenticationResponse.builder()
                                .accessToken(tuple.getT1())
                                .refreshToken(tuple.getT2())
                                .tokenType(tokenType)
                                .build()
                )
                .doOnSuccess(res -> log.info("Login successful for email: {}", request.getEmail()))
                .doOnError(ex -> log.error("Login failed for email: {}", request.getEmail(), ex));
    }

    @Override
    public Mono<Void> register(RegistrationRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());
        final String userRole = "USER";
        return checkUserEmail(request.getEmail())
                .then(checkUserPhoneNumber(request.getPhoneNumber()))
                .then(checkPassword(request.getPassword(), request.getConfirmPassword()))
                .then(userMapper.toUser(request, userRole))
                .flatMap(userRepository::save)
                .doOnSuccess(v -> log.info("Registration successful for email: {}", request.getEmail()))
                .doOnError(ex -> log.error("Registration failed for email: {}", request.getEmail(), ex))
                .then();
    }

    private Mono<Void> checkUserEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(email)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkUserPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkPassword(String password, String confirmPassword) {
        return Mono.defer(() -> {
            if (password == null || !password.equals(confirmPassword)) {
                return Mono.error(new BusinessException(ErrorCode.PASSWORD_MISMATCH));
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<AuthenticationResponse> refreshToken(RefreshRequest request) {
        log.info("Refresh token attempt");
        String tokenType = "Bearer";

        return jwtService.refreshAccessToken(request.getRefreshToken())
                .map(accessToken -> AuthenticationResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(request.getRefreshToken())
                        .tokenType(tokenType)
                        .build())
                .doOnSuccess(res -> log.info("Refresh token successful"))
                .doOnError(ex -> log.error("Refresh token failed", ex));
    }
}
