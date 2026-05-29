package com.MSyamsandiYW.auth_service.security;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyUtils {

    private KeyUtils() {}

    public static Mono<PrivateKey> loadPrivateKey(final String pemPath) {
        return readKeyFromResource(pemPath)
                .map(raw -> raw
                        .replace("-----BEGIN PRIVATE KEY-----","")
                        .replace("-----END PRIVATE KEY-----","")
                        .replaceAll("\\s",""))
                .map(key -> {
                    try {
                        final byte[] decoded = Base64.getDecoder().decode(key);
                        final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
                        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
                    }catch (Exception e){
                        throw new RuntimeException("Failed to load private key", e);
                    }
                });
    }

    public static Mono<PublicKey> loadPublicKey(final String pemPath) {
        return readKeyFromResource(pemPath)
                .map(raw -> raw
                        .replace("-----BEGIN PUBLIC KEY-----","")
                        .replace("-----END PUBLIC KEY-----","")
                        .replaceAll("\\s",""))
                .map(key -> {
                    try {
                        final byte[] decoded = Base64.getDecoder().decode(key);
                        final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
                        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
                    }catch (Exception e){
                        throw new RuntimeException("Failed to load public key", e);

                    }
                });
    }

    private static Mono<String> readKeyFromResource(final String path) {
        return Mono.fromCallable(() -> {
            try (final InputStream is = KeyUtils.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null){
                    throw new IllegalArgumentException("Key not found: " + path);
                }
                return new String(is.readAllBytes());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
