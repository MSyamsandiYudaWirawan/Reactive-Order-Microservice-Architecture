package com.MSyamsandiYW.order_service.order.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.order_service.client.InventoryServiceClient;
import com.MSyamsandiYW.order_service.client.request.GetProductsRequest;
import com.MSyamsandiYW.order_service.client.response.GetProductResponse;
import com.MSyamsandiYW.order_service.discount.DiscountService;
import com.MSyamsandiYW.order_service.kafka.event.OrderCommandPayload;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order.OrderService;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import com.MSyamsandiYW.order_service.order.response.CreateOrderResponse;
import com.MSyamsandiYW.order_service.order.response.GetStatusOrderResponse;
import com.MSyamsandiYW.order_service.order_item.OrderItem;
import com.MSyamsandiYW.order_service.order_item.OrderItemRepository;
import com.MSyamsandiYW.order_service.order_item.request.OrderItemRequest;
import com.MSyamsandiYW.order_service.order_ledger.OrderStatusHistoryService;
import com.MSyamsandiYW.order_service.outbox.Outbox;
import com.MSyamsandiYW.order_service.outbox.OutboxService;
import com.MSyamsandiYW.order_service.properties.AppConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final JwtService jwtService;
    private final OrderItemRepository orderItemRepository;
    private final TransactionalOperator transactionalOperator;
    private final DiscountService discountService;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final InventoryServiceClient inventoryServiceClient;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<ResponseEntity<CreateOrderResponse>> createOrder(String correlationId, String token, CreateOrderRequest request) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Creating order - correlationId: {}, transactionId: {}", correlationId, transactionId);

        return Mono.zip(jwtService.extractClaims(token), inventoryServiceClient.getProductsById(token, buildProductsRequest(request), correlationId)
                        .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.INVENTORY_SERVICE_UNAVAILABLE))))

                // build OrderContext with claims and pricemap
                .map(tuple ->
                        new OrderContext(tuple.getT1(), buildPriceMap(tuple.getT2()), null, null))

                // build Order and OrderItem
                .map(ctx -> {
                    BigDecimal totalAmount = calculateTotalAmount(request.getItems(), ctx.priceMap);
                    String userId = ctx.claims.get("userId").toString();

                    return new OrderContext(
                            ctx.claims,
                            ctx.priceMap,
                            buildOrder(userId, correlationId, transactionId, totalAmount),
                            buildOrderItems(transactionId, correlationId, request.getItems(), ctx.priceMap)
                    );
                })
                // apply discount if any
                .flatMap(ctx -> discountService.apply(request, ctx.order)
                        .map(discountedOrder -> new OrderContext(ctx.claims, ctx.priceMap, discountedOrder, ctx.items)))
                // save order,items,outbox,order status in one transaction
                .flatMap(ctx -> orderRepository.save(ctx.order)
                        // save order items
                        .flatMap(savedOrder -> orderItemRepository.saveAll(ctx.items).collectList().thenReturn(savedOrder))
                        // insert outbox
                        .flatMap(savedItems -> insertOutbox(savedItems, ctx.items))
                        // record order status history
                        .then(orderStatusHistoryService.recordOrderStatus(ctx.order))
                        // flag it as transactional
                        .as(transactionalOperator::transactional)
                        .thenReturn(ctx.order)
                )

                .doOnSuccess(order -> log.info("Order created - orderId: {}, correlationId: {}", order.getId(), correlationId))
                .doOnError(e -> log.error("Failed to create order - correlationId: {}, error: {}", correlationId, e.getMessage()))

                .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED)
                        .body(CreateOrderResponse.builder()
                                .transactionId(transactionId)
                                .build())));


    }

    private Mono<Void> insertOutbox(Order order, List<OrderItem> items) {
        List<OrderItemRequest> requests = items.stream().map(item -> OrderItemRequest.builder()
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .build()).toList();

        OrderCommandPayload payload = OrderCommandPayload.builder()
                .orderId(order.getId().toString())
                .transactionId(order.getTransactionId())
                .correlationId(order.getCorrelationId())
                .items(requests)
                .build();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> Outbox.builder()
                        .aggregateId(order.getTransactionId())
                        .aggregateType(AppConstant.TOPICS.STOCK_RESERVE_REQUESTED)
                        .eventType("STOCK_RESERVE_REQUESTED")
                        .payload(json)
                        .build())
                .flatMap(outboxService::save)
                .then();
    }

    private GetProductsRequest buildProductsRequest(CreateOrderRequest request) {
        return GetProductsRequest.builder()
                .productIds(request.getItems().stream().map(OrderItemRequest::getProductId).toList())
                .build();
    }

    private Map<String, BigDecimal> buildPriceMap(List<GetProductResponse> products) {
        // no need validation since already validated by inventory service
        return products.stream().collect(Collectors.toMap(GetProductResponse::getProductId, GetProductResponse::getPrice));
    }

    private Order buildOrder(String userId, String correlationId, String transactionId, BigDecimal totalAmount) {
        return Order.builder()
                .transactionId(transactionId)
                .correlationId(correlationId)
                .userId(userId)
                .orderStatus(AppConstant.ORDER_STATUS.PENDING.name())
                .totalAmount(totalAmount)
                .createdBy("ORDER_SERVICE")
                .createdAt(Instant.now())
                .build();
    }

    private List<OrderItem> buildOrderItems(String transactionId, String correlationId, List<OrderItemRequest> items, Map<String, BigDecimal> priceMap) {
        return items.stream()
                .map(item -> OrderItem.builder()
                        .transactionId(transactionId)
                        .correlationId(correlationId)
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .price(priceMap.get(item.getProductId()))
                        .build())
                .toList();
    }

    private BigDecimal calculateTotalAmount(List<OrderItemRequest> items, Map<String, BigDecimal> priceMap) {
        return items.stream()
                .map(item -> priceMap.get(item.getProductId()).multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<ResponseEntity<GetStatusOrderResponse>> getStatusOrder(String token, String transactionId, String correlationId) {
        log.info("Getting status order - correlationId: {}, transactionId: {}", correlationId, transactionId);
        // extract claims and validate userId from token
        return Mono.zip(
                        jwtService.extractClaims(token),
                        orderRepository.findByTransactionId(transactionId)
                                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND))))
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
                                .correlationId(order.getCorrelationId())
                                .orderStatus(order.getOrderStatus())
                                .totalAmount(order.getTotalAmount())
                                .discountCode(order.getDiscountCode())
                                .createdAt(order.getCreatedAt())
                                .build())
                );
    }

    @Override
    public Mono<ResponseEntity<List<GetStatusOrderResponse>>> getUserOrders(String token) {
        //extract claims
        return jwtService.extractClaims(token)
                //validate userId from token
                .flatMap(claims -> {
                    String userId = claims.get("userId").toString();
                    if (userId == null || userId.isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    }
                    return orderRepository.findAllByUserId(userId).collectList();
                })
                .map(orders ->
                        ResponseEntity.ok().body(orders.stream().map(order -> GetStatusOrderResponse.builder()
                                .transactionId(order.getTransactionId())
                                .correlationId(order.getCorrelationId())
                                .orderStatus(order.getOrderStatus())
                                .totalAmount(order.getTotalAmount())
                                .discountCode(order.getDiscountCode())
                                .createdAt(order.getCreatedAt())
                                .build()
                        ).toList())
                )
                ;
    }

    private record OrderContext(Claims claims, Map<String, BigDecimal> priceMap, Order order, List<OrderItem> items) {
    }
}


