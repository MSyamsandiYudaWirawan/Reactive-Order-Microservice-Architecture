package com.MSyamsandiYW.common.jwt;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import reactor.core.publisher.Mono;

import java.security.PublicKey;

public class JwtService {
    private static final String PUBLIC_KEY_PATH = "keys/public_key.pem";
    private final Mono<PublicKey> publicKey;

    public JwtService() {
        this.publicKey = KeyUtils.loadPublicKey(PUBLIC_KEY_PATH).cache();
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
}
