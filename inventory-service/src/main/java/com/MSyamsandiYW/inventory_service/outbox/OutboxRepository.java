package com.MSyamsandiYW.inventory_service.outbox;

import com.MSyamsandiYW.inventory_service.outbox.Outbox;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface OutboxRepository extends R2dbcRepository<Outbox, UUID> {
}
