package com.MSyamsandiYW.auth_service.user;

import com.MSyamsandiYW.auth_service.user.request.ChangePasswordRequest;
import com.MSyamsandiYW.auth_service.user.request.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User API")
public class UserController {
    private final UserService userService;

    @PatchMapping("/me")
    @ResponseStatus(code = NO_CONTENT)
    public Mono<Void> updateProfileInfo(
            @RequestBody
            @Valid final ProfileUpdateRequest request,
            final Authentication principal
    ) {
        return getEmail(principal)
                .flatMap(email -> userService.updateProfileInfo(request, email));
    }

    @PostMapping("/me/password")
    @ResponseStatus(code = NO_CONTENT)
    public Mono<Void> changePassword(
            @RequestBody
            @Valid final ChangePasswordRequest request,
            final Authentication principal
    ) {
        return getEmail(principal)
                .flatMap(email -> userService.changePassword(request, email));
    }

    @PatchMapping("/me/deactivated")
    @ResponseStatus(code = NO_CONTENT)
    public Mono<Void> deactivateAccount(final Authentication principal) {
        return getEmail(principal)
                .flatMap(userService::deactivateAccount);
    }

    @PatchMapping("/me/reactivated")
    @ResponseStatus(code = NO_CONTENT)
    public Mono<Void> reactivatedAccount(final Authentication principal) {
        return getEmail(principal)
                .flatMap(userService::reactivateAccount);
    }

    @DeleteMapping("/me")
    @ResponseStatus(code = NO_CONTENT)
    public Mono<Void> deleteAccount(final Authentication principal) {
        return getEmail(principal)
                .flatMap(userService::deleteAccount);
    }


    private Mono<String> getEmail(Authentication principal) {
        return Mono.just(((User) principal.getPrincipal()).getEmail());
    }
}
