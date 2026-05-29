package com.MSyamsandiYW.auth_service.user;

import com.MSyamsandiYW.auth_service.auth.request.RegistrationRequest;
import com.MSyamsandiYW.auth_service.user.request.ProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor

@Component
public class UserMapper {
    private final PasswordEncoder passwordEncoder;

    public Mono<User> mergerUserInfo(final User user, final ProfileUpdateRequest request) {
        if (StringUtils.isNotBlank(request.getName())
                && !user.getName().equals(request.getName())) {
            user.setName(request.getName());
        }
        return Mono.just(user);
    }

    public Mono<User> toUser(RegistrationRequest request, String userRole) {
        return Mono.just(User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(userRole)
                .enabled(true)
                .locked(false)
                .credentialsExpired(false)
                .emailVerified(false)
                .phoneVerified(false)
                .build());
    }
}
