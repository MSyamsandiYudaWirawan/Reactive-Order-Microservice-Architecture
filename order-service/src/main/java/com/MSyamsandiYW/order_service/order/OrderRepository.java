package com.MSyamsandiYW.order_service.order;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface OrderRepository extends R2dbcRepository<Order, UUID> {

}
