package com.MSyamsandiYW.auth_service.security;

import com.MSyamsandiYW.auth_service.exception.BusinessException;
import com.MSyamsandiYW.auth_service.exception.ErrorCode;
import com.MSyamsandiYW.auth_service.user.User;
import com.MSyamsandiYW.auth_service.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


@Service
public class JwtService {

    private static final String TOKEN_TYPE = "token_type";
    private final Mono<PrivateKey> privateKey;
    private final Mono<PublicKey> publicKey;
    @Value("${app.security.jwt.access-token-expiration}")
    private Long accessTokenExpiration;
    @Value("${app.security.jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    private UserRepository userRepository;

    public JwtService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.privateKey = KeyUtils.loadPrivateKey("keys/local-only/private_key.pem");
        this.publicKey = KeyUtils.loadPublicKey("keys/local-only/public_key.pem");
    }


    public Mono<String> generateAccessToken(final String email) {
        final Map<String, Object> claims = new HashMap<>();
        return findUserByEmail(email)
                .doOnNext(user -> {
                    claims.put(TOKEN_TYPE, "ACCESS_TOKEN");
                    populateClaims(claims, user);
                })
                .flatMap(user -> buildToken(user, claims, accessTokenExpiration));
    }


    public Mono<String> generateRefreshToken(final String email) {
        final Map<String, Object> claims = new HashMap<>();
        return findUserByEmail(email)
                .doOnNext(user -> {
                    claims.put(TOKEN_TYPE, "REFRESH_TOKEN");
                    populateClaims(claims, user);
                })
                .flatMap(user -> buildToken(user, claims, refreshTokenExpiration));
    }

    public Mono<Void> validateToken(final String token, final String expectedEmail) {
        return extractClaims(token)
                .flatMap(claims -> {
                    if (claims.getExpiration().before(new Date()))
                        return Mono.error(new BusinessException(ErrorCode.TOKEN_EXPIRED));
                    if (!claims.getSubject().equalsIgnoreCase(expectedEmail))
                        return Mono.error(new BusinessException(ErrorCode.USER_UNAUTHORIZED));
                    return Mono.empty();
                });
    }

    public Mono<String> refreshAccessToken(final String refreshToken){
        return extractClaims(refreshToken)
                .flatMap(claims -> {
                    if(!"REFRESH_TOKEN".equals(claims.get(TOKEN_TYPE))){
                        return Mono.error(new BusinessException(ErrorCode.INVALID_TOKEN));
                    }
                    if (claims.getExpiration().before(new Date())){
                        return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED));
                    }
                  return Mono.just(claims.getSubject());
                })
                .flatMap(this::generateAccessToken);
    }

    public Mono<Claims> extractClaims(final String token) {
        return publicKey.map(key ->
                Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
        ).onErrorResume(e -> Mono.error(new BusinessException(ErrorCode.TOKEN_SIGNATURE_INVALID)));
    }

    private Mono<String> buildToken(final User user, final Map<String, Object> claims, final Long tokenExpiration) {
        return privateKey.map(key ->
                Jwts.builder()
                        .claims(claims)
                        .subject(user.getEmail())
                        .issuedAt(new Date(System.currentTimeMillis()))
                        .expiration(new Date(System.currentTimeMillis() + tokenExpiration))
                        .signWith(key)
                        .compact()
        );
    }


    private Mono<User> findUserByEmail(final String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND, email)));
    }

    private void populateClaims(Map<String, Object> claims, User user) {
        claims.put("userName", user.getName());
        claims.put("userEmail", user.getEmail());
        claims.put("userRole", user.getRoles());
        claims.put("phoneNumber", user.getPhoneNumber());
        claims.put("locked", user.isLocked());
        claims.put("credentialsExpired", user.isCredentialsExpired());
        claims.put("enabled", user.isEnabled());
        claims.put("emailVerified", user.isEmailVerified());
        claims.put("phoneVerified", user.isPhoneVerified());
        claims.put("deleted", user.isDeleted());
    }
}
