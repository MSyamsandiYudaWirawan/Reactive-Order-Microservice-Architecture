package com.MSyamsandiYW.auth_service.user;

import com.MSyamsandiYW.auth_service.user.request.ChangePasswordRequest;
import com.MSyamsandiYW.auth_service.user.request.ProfileUpdateRequest;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import reactor.core.publisher.Mono;

public interface UserService extends ReactiveUserDetailsService {

    Mono<Void> updateProfileInfo(ProfileUpdateRequest request, String email);
    Mono<Void> changePassword(ChangePasswordRequest request, String email);
    Mono<Void> deactivateAccount(String email);
    Mono<Void> reactivateAccount(String email);
    Mono<Void> deleteAccount(String email);
}
