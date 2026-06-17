package com.MSyamsandiYW.auth_service.user.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.auth_service.user.User;
import com.MSyamsandiYW.auth_service.user.UserMapper;
import com.MSyamsandiYW.auth_service.user.UserRepository;
import com.MSyamsandiYW.auth_service.user.UserService;
import com.MSyamsandiYW.auth_service.user.request.ChangePasswordRequest;
import com.MSyamsandiYW.auth_service.user.request.ProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;


    @Override
    public Mono<UserDetails> findByUsername(String email) {
        return findUserByEmail(email)
                .map(user -> (UserDetails) user)
                .onErrorMap(BusinessException.class, ex -> new UsernameNotFoundException(ex.getMessage()));
    }

    @Override
    public Mono<Void> updateProfileInfo(ProfileUpdateRequest request, String email) {
        log.info("Update profile attempt for email: {}", email);
        return findUserByEmail(email)
                .flatMap(user -> userMapper.mergerUserInfo(user, request))
                .flatMap(userRepository::save)
                .doOnSuccess(v -> log.info("Profile updated for user: {}", email))
                .doOnError(ex -> log.error("Failed to update profile for: {}", email, ex))
                .then();
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request, String email) {
        log.info("Change password attempt for email: {}", email);
        return Mono.defer(() -> {
            if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
                return Mono.error(new BusinessException(ErrorCode.CHANGE_PASSWORD_MISMATCH));
            }
            return findUserByEmail(email)
                    .flatMap(user -> {
                        if (!this.passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                            return Mono.error(new BusinessException(ErrorCode.PASSWORD_MISMATCH));
                        }
                        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                        return Mono.just(user);
                    })
                    .flatMap(userRepository::save)
                    .doOnSuccess(v -> log.info("Password changed for user: {}", email))
                    .doOnError(ex -> log.error("Failed to change password for: {}", email, ex))
                    .then();
        });
    }

    @Override
    public Mono<Void> deactivateAccount(String email) {
        log.info("Deactivate account attempt for email: {}", email);
        return findUserByEmail(email)
                .flatMap(user -> {
                    if (!user.isEnabled()) {
                        return Mono.error(new BusinessException(ErrorCode.ACCOUNT_ALREADY_DEACTIVATED));
                    }
                    user.setEnabled(false);
                    return Mono.just(user);
                })
                .flatMap(userRepository::save)
                .doOnSuccess(v -> log.info("Account deactivated for user: {}", email))
                .doOnError(ex -> log.error("Failed to deactivate account for: {}", email, ex))
                .then();
    }

    @Override
    public Mono<Void> reactivateAccount(String email) {
        log.info("Reactivate account attempt for email: {}", email);
        return findUserByEmail(email)
                .flatMap(user -> {
                    if (user.isEnabled()) {
                        return Mono.error(new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVATED));
                    }
                    user.setEnabled(true);
                    return Mono.just(user);
                })
                .flatMap(userRepository::save)
                .doOnSuccess(v -> log.info("Account reactivated for user: {}", email))
                .doOnError(ex -> log.error("Failed to reactivate account for: {}", email, ex))
                .then();
    }

    @Override
    public Mono<Void> deleteAccount(String email) {
        log.info("Delete account attempt for email: {}", email);
        return findUserByEmail(email)
                .flatMap(user -> {
                    if (user.isDeleted()) {
                        return Mono.error(new BusinessException(ErrorCode.ACCOUNT_ALREADY_DELETED));
                    }
                    user.setDeleted(true);
                    //also deactivate the account
                    user.setEnabled(false);
                    return Mono.just(user);
                })
                .flatMap(userRepository::save)
                .doOnSuccess(v -> log.info("Account soft-deleted for user: {}", email))
                .doOnError(ex -> log.error("Failed to delete account for: {}", email, ex))
                .then();
    }


    private Mono<User> findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND, email)));
    }

}
