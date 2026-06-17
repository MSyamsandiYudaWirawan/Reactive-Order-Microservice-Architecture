package com.MSyamsandiYW.inventory_service.stock_ledger;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface StockLedgerRepository extends R2dbcRepository<StockLedger, UUID> {


}
