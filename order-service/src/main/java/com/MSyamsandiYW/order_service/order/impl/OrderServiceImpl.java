package com.MSyamsandiYW.order_service.order.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.order_service.discount.DiscountService;
import com.MSyamsandiYW.order_service.kafka.OrderEventProducer;
import com.MSyamsandiYW.order_service.kafka.request.StockReserveRequest;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order.OrderService;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import com.MSyamsandiYW.order_service.order.response.CreateOrderResponse;
import com.MSyamsandiYW.order_service.order.response.GetUserOrdersResponse;
import com.MSyamsandiYW.order_service.order.response.GetStatusOrderResponse;
import com.MSyamsandiYW.order_service.order_item.OrderItem;
import com.MSyamsandiYW.order_service.order_item.OrderItemRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerService;
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
    private final DiscountService discountService;
    private final OrderLedgerService orderLedgerService;

    @Override
    public Mono<ResponseEntity<CreateOrderResponse>> createOrder(String correlationId, String token, CreateOrderRequest request) {
        //TODO validate item prices against inventory-service product catalog
        //TODO consider implement orderItemService for item validation
        String transactionId = UUID.randomUUID().toString();
        log.info("Creating order - correlationId: {}, transactionId: {}", correlationId, transactionId);

        // extract claims
        return jwtService.extractClaims(token)
                .map(claims -> {
                    //calculate total amount
                    double totalAmount = request.getItems().stream()
                            .mapToDouble(item -> item.getPrice() * item.getQuantity())
                            .sum();

                    //create order object
                    return Order.builder()
                            .correlationId(correlationId)
                            .transactionId(transactionId)
                            .userId(claims.get("userId").toString())
                            .orderStatus(AppConstant.ORDER_STATUS.PENDING.name())
                            .totalAmount(totalAmount)
                            .createdBy("SYSTEM")
                            .createdDate(ZonedDateTime.now())
                            .build();
                })
                // apply discount if valid
                .flatMap(order -> discountService.apply(request, order))
                // save order and items in database
                .flatMap(order -> saveOrderWithItems(order, request))
                .doOnSuccess(order ->
                        log.info("Order persisted - orderId: {}, correlationId: {}",
                                order.getId(), correlationId))

                // record order event to ledger
                .flatMap(order -> orderLedgerService.recordOrderEvent(order).thenReturn(order))

                // produce event to reserve stock consumed by  inventory-service
                .flatMap(order -> {
                    StockReserveRequest stockReserveRequest = StockReserveRequest.builder()
                            .orderId(order.getId().toString())
                            .transactionId(transactionId)
                            .correlationId(correlationId)
                            .items(request.getItems())
                            .build();

                    //key is UUID random purposely for eventId
                    return orderEventProducer.send(
                            AppConstant.TOPICS.STOCK_RESERVE_REQUESTED, UUID.randomUUID().toString(), stockReserveRequest);
                })
                .doOnSuccess(unused ->
                        log.info("Stock reserve event published - correlationId: {}", correlationId))
                .doOnError(e ->
                        log.error("Failed to create order - correlationId: {}, error: {}",
                                correlationId, e.getMessage()))
                // handle order failed
                .onErrorResume(e -> handleError(transactionId).then(Mono.error(e)))
                // return to client
                .then(Mono.just(
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(CreateOrderResponse.builder()
                                        .transactionId(transactionId)
                                        .build())
                ));
    }

    @Override
    public Mono<ResponseEntity<GetStatusOrderResponse>> getStatusOrder(String token, String transactionId) {
        // extract claims and validate userId from token
        return Mono.zip(jwtService.extractClaims(token), orderRepository.findByTransactionId(transactionId))
                //validate userId with order user
                .flatMap(tuple -> {
                    String userId = tuple.getT1().get("userId").toString();
                    if (userId == null || userId.isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    }
                    Order order = tuple.getT2();
                    if (!userId.equalsIgnoreCase(order.getUserId())) {
                        return Mono.error(new BusinessException(ErrorCode.USER_UNAUTHORIZED));
                    }
                    return Mono.just(order);
                })
                // mapping order to client
                .map(order -> ResponseEntity.status(HttpStatus.OK)
                        .body(GetStatusOrderResponse
                                .builder()
                                .transactionId(order.getTransactionId())
                                .orderStatus(order.getOrderStatus())
                                .totalAmount(order.getTotalAmount())
                                .paymentMethod(order.getPaymentMethod())
                                .discountCode(order.getDiscountCode())
                                .createdDate(order.getCreatedDate())
                                .build())
                );
    }

    @Override
    public Mono<ResponseEntity<GetUserOrdersResponse>> getUserOrders(String token) {
        //extract claims
        return jwtService.extractClaims(token)
                //validate userId from token
                .flatMap(claims -> {
                    String userId = claims.get("userId").toString();
                    if (userId == null || userId.isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    }
                    return orderRepository.findByUserId(userId);
                })
                .map(orders -> {
                    List<GetStatusOrderResponse> orderList = orders.stream().map(order -> GetStatusOrderResponse.builder()
                            .transactionId(order.getTransactionId())
                            .orderStatus(order.getOrderStatus())
                            .totalAmount(order.getTotalAmount())
                            .paymentMethod(order.getPaymentMethod())
                            .discountCode(order.getDiscountCode())
                            .createdDate(order.getCreatedDate())
                            .build()).toList();

                    return ResponseEntity.ok().body(GetUserOrdersResponse.builder()
                            .orders(orderList)
                            .build());
                })
                ;
    }

    private Mono<Order> saveOrderWithItems(Order order, CreateOrderRequest request) {
        return orderRepository.save(order)
                .flatMap(saved -> {
                    List<OrderItem> orderItems = request.getItems().stream()
                            .map(item -> OrderItem.builder()
                                    .transactionId(saved.getTransactionId())
                                    .correlationId(saved.getCorrelationId())
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
                })
                .then();
    }
}
