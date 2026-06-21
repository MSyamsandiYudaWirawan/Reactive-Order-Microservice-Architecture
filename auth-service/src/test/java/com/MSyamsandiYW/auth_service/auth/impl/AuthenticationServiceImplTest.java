package com.MSyamsandiYW.auth_service.auth.impl;

import com.MSyamsandiYW.auth_service.auth.request.AuthenticationRequest;
import com.MSyamsandiYW.auth_service.auth.request.RefreshRequest;
import com.MSyamsandiYW.auth_service.auth.request.RegistrationRequest;
import com.MSyamsandiYW.auth_service.security.JwtService;
import com.MSyamsandiYW.auth_service.user.User;
import com.MSyamsandiYW.auth_service.user.UserMapper;
import com.MSyamsandiYW.auth_service.user.UserRepository;
import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private ReactiveAuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("login - happy path should return tokens")
    void login_happyPath() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("test@test.com");
        request.setPassword("password");

        User user = User.builder()
                .email("test@test.com")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(Mono.just(auth));
        when(jwtService.generateAccessToken("test@test.com")).thenReturn(Mono.just("access-token"));
        when(jwtService.generateRefreshToken("test@test.com")).thenReturn(Mono.just("refresh-token"));

        StepVerifier.create(authenticationService.login(request))
                .assertNext(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("access-token");
                    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
                    assertThat(response.getTokenType()).isEqualTo("Bearer");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("login - bad credentials should propagate error")
    void login_badCredentials() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("bad@test.com");
        request.setPassword("wrong");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(Mono.error(new RuntimeException("Bad credentials")));

        StepVerifier.create(authenticationService.login(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("register - email already exists should error")
    void register_emailAlreadyExists() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("exists@test.com");
        request.setPassword("password");
        request.setConfirmPassword("password");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmailIgnoreCase("exists@test.com")).thenReturn(Mono.just(true));

        StepVerifier.create(authenticationService.register(request))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.EMAIL_ALREADY_EXISTS)
                .verify();
    }

    @Test
    @DisplayName("register - password mismatch should error")
    void register_passwordMismatch() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new@test.com");
        request.setPassword("password1");
        request.setConfirmPassword("password2");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmailIgnoreCase("new@test.com")).thenReturn(Mono.just(false));
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(Mono.just(false));

        StepVerifier.create(authenticationService.register(request))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.PASSWORD_MISMATCH)
                .verify();
    }

    @Test
    @DisplayName("register - happy path should save user")
    void register_happyPath() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new@test.com");
        request.setPassword("password");
        request.setConfirmPassword("password");
        request.setPhoneNumber("1234567890");

        User user = User.builder().email("new@test.com").build();

        when(userRepository.existsByEmailIgnoreCase("new@test.com")).thenReturn(Mono.just(false));
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(Mono.just(false));
        when(userMapper.toUser(request, "USER")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));

        StepVerifier.create(authenticationService.register(request))
                .verifyComplete();

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("refreshToken - happy path should return new access token")
    void refreshToken_happyPath() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid-refresh-token");

        when(jwtService.refreshAccessToken("valid-refresh-token")).thenReturn(Mono.just("new-access-token"));

        StepVerifier.create(authenticationService.refreshToken(request))
                .assertNext(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("new-access-token");
                    assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
                    assertThat(response.getTokenType()).isEqualTo("Bearer");
                })
                .verifyComplete();
    }
}
