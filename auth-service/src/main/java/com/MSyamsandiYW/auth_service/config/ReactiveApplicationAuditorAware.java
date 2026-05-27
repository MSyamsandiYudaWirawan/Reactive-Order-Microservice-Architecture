package com.MSyamsandiYW.auth_service.config;

import com.MSyamsandiYW.auth_service.user.User;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

public class ReactiveApplicationAuditorAware implements ReactiveAuditorAware<String> {
    @Override
    public Mono<String> getCurrentAuditor() {
        //get current user
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> {
                    if (auth == null || !auth.isAuthenticated()
                            || auth instanceof AnonymousAuthenticationToken) {
                        return "";
                    }
                    final User user = (User) auth.getPrincipal();
                    return user.getUsername();
                });
    }
}

