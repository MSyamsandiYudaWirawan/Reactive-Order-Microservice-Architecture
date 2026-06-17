package com.MSyamsandiYW.order_service.order_ledger;

import com.MSyamsandiYW.order_service.order.Order;
import reactor.core.publisher.Mono;

public interface OrderLedgerService {
    Mono<Void> recordOrderEvent(Order order);
}
