package com.MSyamsandiYW.order_service.discount;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DiscountRepository extends R2dbcRepository<Discount, UUID> {
    Mono<Discount> findByCode(String couponCode);
}
