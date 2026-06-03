package com.MSyamsandiYW.gateway_service.security;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TokenValidator {
    private final JwtService jwtService;
    private static final String TOKEN_TYPE = "token_type";

    public Mono<Void> validateToken(final String token) {
        return jwtService.extractClaims(token)
                .flatMap(claims -> {
                    if (!"ACCESS_TOKEN".equals(claims.get(TOKEN_TYPE))) {
                        return Mono.error(new BusinessException(ErrorCode.INVALID_TOKEN));
                    }
                    if (claims.getSubject() == null || claims.getSubject().isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.USER_UNAUTHORIZED));
                    }
                    if (Boolean.TRUE.equals(claims.get("isLocked"))) {
                        return Mono.error(new BusinessException(ErrorCode.ACCOUNT_LOCKED));
                    }
                    if (Boolean.TRUE.equals(claims.get("isDeleted"))) {
                        return Mono.error(new BusinessException(ErrorCode.ACCOUNT_ALREADY_DELETED));
                    }
                    if (Boolean.TRUE.equals(claims.get("isCredentialsExpired"))) {
                        return Mono.error(new BusinessException(ErrorCode.PASSWORD_EXPIRED));
                    }
                    if (Boolean.FALSE.equals(claims.get("isEnabled"))) {
                        return Mono.error(new BusinessException(ErrorCode.ERR_USER_DISABLED));
                    }
                    return Mono.empty();

                });
    }
}
