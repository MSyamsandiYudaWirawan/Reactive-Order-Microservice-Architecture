package com.MSyamsandiYW.order_service.discount;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DiscountRepository extends R2dbcRepository<Discount, UUID> {
    Mono<Discount> findByCode(String couponCode);


    @Query("""
            UPDATE discounts SET max_usage = max_usage - 1, updated_by = 'ORDER_SERVICE', updated_at = now()
            WHERE code = :code AND max_usage > 0
            """)
    @Modifying
    Mono<Integer> updateDiscount(String code);
}
