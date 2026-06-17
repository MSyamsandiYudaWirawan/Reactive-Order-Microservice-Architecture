package com.MSyamsandiYW.order_service.order_ledger;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface OrderLedgerRepository extends R2dbcRepository<OrderLedger, UUID> {
}
