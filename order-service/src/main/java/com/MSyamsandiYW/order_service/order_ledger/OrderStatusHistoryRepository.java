package com.MSyamsandiYW.order_service.order_ledger;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface OrderStatusHistoryRepository extends R2dbcRepository<OrderStatusHistory, UUID> {
}
