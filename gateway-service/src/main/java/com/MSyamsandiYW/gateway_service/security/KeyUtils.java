package com.MSyamsandiYW.gateway_service.security;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyUtils {

    private KeyUtils() {
    }

    public static Mono<PublicKey> loadPublicKey(final String path) {
        return readFromResource(path)
                .map(raw -> raw
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", ""))
                .map(key -> {
                    try {
                        final byte[] decode = Base64.getDecoder().decode(key);
                        final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decode);
                        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load public key", e);
                    }
                });
    }

    private static Mono<String> readFromResource(final String path) {
        return Mono.fromCallable(() -> {
            try (final InputStream is = KeyUtils.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    throw new IllegalArgumentException("key not found: " + path);
                }
                return new String(is.readAllBytes());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
