package com.MSyamsandiYW.order_service.order.impl;

import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.order_service.kafka.OrderEventProducer;
import com.MSyamsandiYW.order_service.kafka.request.StockReserveRequest;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order.OrderService;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import com.MSyamsandiYW.order_service.order.response.CreateOrderResponse;
import com.MSyamsandiYW.order_service.order.response.GetOrdersResponse;
import com.MSyamsandiYW.order_service.order.response.GetStatusOrderResponse;
import com.MSyamsandiYW.order_service.order_item.OrderItem;
import com.MSyamsandiYW.order_service.order_item.OrderItemRepository;
import com.MSyamsandiYW.order_service.properties.AppConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final JwtService jwtService;
    private final OrderItemRepository orderItemRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<ResponseEntity<CreateOrderResponse>> createOrder(String correlationId, String token, CreateOrderRequest request) {
        //todo check idempotence in redis in gateway not here
        String transactionId = UUID.randomUUID().toString();
        log.info("Creating order - correlationId: {}, transactionId: {}", correlationId, transactionId);

        return jwtService.extractClaims(token)
                .map(claims -> {
                            double totalAmount = request.getItems().stream()
                                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                                    .sum();

                            return Order.builder()
                                    .correlationId(correlationId)
                                    .transactionId(transactionId)
                                    .userId(claims.get("userId").toString())
                                    .orderStatus(AppConstant.ORDER_STATUS.PENDING.name())
                                    .totalAmount(totalAmount)
                                    .createdBy("SYSTEM")
                                    .createdDate(ZonedDateTime.now())
                                    .build();
                        }

                )
                .flatMap(order -> saveOrderWithItems(order, request))
                .doOnSuccess(order -> log.info("Order persisted - orderId: {}, correlationId: {}", order.getId(), correlationId))
                .flatMap(order -> {
                    StockReserveRequest stockReserveRequest = StockReserveRequest.builder()
                            .orderId(order.getId().toString())
                            .transactionId(transactionId)
                            .correlationId(correlationId)
                            .items(request.getItems())
                            .build();
                    return orderEventProducer.send(AppConstant.TOPICS.STOCK_RESERVE_REQUESTED, correlationId, stockReserveRequest);
                })
                .doOnSuccess(unused -> log.info("Stock reserve event published - correlationId: {}", correlationId))
                .doOnError(e -> log.error("Failed to create order - correlationId: {}, error: {}", correlationId, e.getMessage()))
                .onErrorResume(e -> handleError(transactionId).then(Mono.error(e)))
                .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(CreateOrderResponse.builder()
                        .transactionId(transactionId)
                        .build()))
                );
    }

    private Mono<Order> saveOrderWithItems(Order order, CreateOrderRequest request) {
        return orderRepository.save(order)
                .flatMap(saved -> {
                    List<OrderItem> orderItems = request.getItems().stream()
                            .map(item -> OrderItem.builder()
                                    .orderId(saved.getId())
                                    .productId(item.getProductId())
                                    .quantity(item.getQuantity())
                                    .price(item.getPrice())
                                    .createdBy("SYSTEM")
                                    .createdDate(ZonedDateTime.now())
                                    .build())
                            .toList();
                    return orderItemRepository.saveAll(orderItems).collectList().thenReturn(saved);
                })
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> handleError(String transactionId) {
        return orderRepository.findByTransactionId(transactionId)
                .flatMap(order -> {
                    order.setOrderStatus(AppConstant.ORDER_STATUS.FAILED.name());
                    return orderRepository.save(order);
                }).then();
    }

    @Override
    public Mono<ResponseEntity<GetStatusOrderResponse>> getStatusOrder(String transactionId) {
        return null;
    }

    @Override
    public Mono<ResponseEntity<GetOrdersResponse>> getUserOrders() {
        return null;
    }
}
