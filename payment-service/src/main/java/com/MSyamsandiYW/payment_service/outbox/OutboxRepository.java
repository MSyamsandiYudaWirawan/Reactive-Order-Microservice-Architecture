package com.MSyamsandiYW.payment_service.outbox;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface OutboxRepository extends R2dbcRepository<Outbox, UUID> {
}
