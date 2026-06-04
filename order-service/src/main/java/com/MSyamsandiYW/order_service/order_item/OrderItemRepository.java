package com.MSyamsandiYW.order_service.order_item;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface OrderItemRepository extends R2dbcRepository<OrderItem, UUID> {
}
