package com.MSyamsandiYW.order_service.order_ledger.impl;

import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order_ledger.OrderStatusHistory;
import com.MSyamsandiYW.order_service.order_ledger.OrderStatusHistoryRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderStatusHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusHistoryServiceImpl implements OrderStatusHistoryService {
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Override
    public Mono<Void> recordOrderEvent(Order order) {
        OrderStatusHistory orderStatusHistory = OrderStatusHistory.builder()
                .transactionId(order.getTransactionId())
                .correlationId(order.getCorrelationId())
                .status(order.getOrderStatus())
                .createdAt(Instant.now())
                .build();

        return orderStatusHistoryRepository.save(orderStatusHistory).then();
    }
}
