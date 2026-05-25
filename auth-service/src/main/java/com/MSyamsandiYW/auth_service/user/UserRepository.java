package com.MSyamsandiYW.auth_service.user;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User,String> {

    Mono<Boolean> existsByEmailIgnoreCase(String email);

    Mono<Boolean> existsByPhoneNumber(String phone);

    Mono<User> findByEmailIgnoreCase(String email);
}
