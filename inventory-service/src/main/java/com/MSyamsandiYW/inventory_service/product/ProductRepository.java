package com.MSyamsandiYW.inventory_service.product;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductRepository extends R2dbcRepository<Product, UUID> {
    @Query("""
            UPDATE products SET available_qty = available_qty - :qty, reserved_qty = reserved_qty + :qty,
                updated_by = "INVENTORY_SERVICE", last_modified_date = now()
            WHERE available_qty >= :qty AND id= :id AND is_active = true AND is_deleted = false;
            """)
    @Modifying
    Mono<Integer> reserveStock(UUID id, Integer qty);

    @Query("""
            UPDATE products SET available_qty = available_qty + :qty, reserved_qty = reserved_qty - :qty,
                updated_by = "INVENTORY_SERVICE", last_modified_date = now()
            WHERE id= :id AND is_active = true AND is_deleted = false;
            """)
    @Modifying
    Mono<Integer> releaseStock(UUID id, Integer qty);

    @Query("""
            UPDATE products SET reserved_qty = reserved_qty - :qty, sold_qty = sold_qty + :qty,
                updated_by = "INVENTORY_SERVICE", last_modified_date = now()
            WHERE id= :id AND is_active = true AND is_deleted = false;
            """)
    @Modifying
    Mono<Integer> deductStock(UUID id, Integer qty);
}
