package com.MSyamsandiYW.order_service.order_ledger.impl;

import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedger;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderLedgerServiceImpl implements OrderLedgerService {
    private final OrderLedgerRepository orderLedgerRepository;

    @Override
    public Mono<Void> recordOrderEvent(Order order) {
        OrderLedger orderLedger = OrderLedger.builder()
                .transactionId(order.getTransactionId())
                .correlationId(order.getCorrelationId())
                .eventType(order.getOrderStatus())
                .createdDate(Instant.now())
                .build();

        return orderLedgerRepository.save(orderLedger).then();
    }
}
