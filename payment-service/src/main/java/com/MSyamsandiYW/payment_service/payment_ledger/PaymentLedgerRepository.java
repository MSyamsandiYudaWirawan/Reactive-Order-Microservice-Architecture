package com.MSyamsandiYW.payment_service.payment_ledger;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface PaymentLedgerRepository extends R2dbcRepository<PaymentLedger, UUID> {
}
