package com.MSyamsandiYW.gateway_service.security;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.security.SignatureException;

@Service
public class JwtService {
    private final Mono<PublicKey> publicKey;
    private static final String TOKEN_TYPE = "token_type";

    public JwtService() {
        this.publicKey = KeyUtils.loadPublicKey("keys/public_key.pem");
    }

    public Mono<Claims> extractClaims(String token) {
        return publicKey.map(key -> Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
        ).onErrorMap(ExpiredJwtException.class,
                e -> new BusinessException(ErrorCode.TOKEN_EXPIRED))
                .onErrorMap(SignatureException.class, e ->
                        new BusinessException(ErrorCode.TOKEN_SIGNATURE_INVALID))
                .onErrorMap(JwtException.class, e ->
                        new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    public Mono<Void> validateToken(final String token) {
        return extractClaims(token)
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
