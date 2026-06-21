package com.MSyamsandiYW.order_service.order.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.order_service.client.InventoryServiceClient;
import com.MSyamsandiYW.order_service.client.response.GetProductResponse;
import com.MSyamsandiYW.order_service.discount.DiscountService;
import com.MSyamsandiYW.order_service.kafka.OrderCommandProducer;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import com.MSyamsandiYW.order_service.order.response.CreateOrderResponse;
import com.MSyamsandiYW.order_service.order.response.GetStatusOrderResponse;
import com.MSyamsandiYW.order_service.order_item.OrderItem;
import com.MSyamsandiYW.order_service.order_item.OrderItemRepository;
import com.MSyamsandiYW.order_service.order_item.request.OrderItemRequest;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerService;
import com.MSyamsandiYW.order_service.properties.AppConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCommandProducer orderCommandProducer;
    @Mock
    private JwtService jwtService;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private DiscountService discountService;
    @Mock
    private OrderLedgerService orderLedgerService;
    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Claims claims;
    private String token;
    private String correlationId;

    @BeforeEach
    void setUp() {
        token = "test-jwt-token";
        correlationId = UUID.randomUUID().toString();

        Map<String, Object> claimsMap = Map.of(
                "userId", "user-123",
                "userEmail", "test@test.com",
                "userRole", "USER"
        );
        claims = new DefaultClaims(claimsMap);
    }

    @Test
    @DisplayName("createOrder - happy path should return CREATED with transactionId")
    void createOrder_happyPath() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId("product-1")
                        .quantity(2)
                        .build()))
                .build();

        GetProductResponse product = GetProductResponse.builder()
                .productId("product-1")
                .price(50.0)
                .build();

        Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .correlationId(correlationId)
                .transactionId(UUID.randomUUID().toString())
                .userId("user-123")
                .orderStatus(AppConstant.ORDER_STATUS.PENDING.name())
                .totalAmount(100.0)
                .createdDate(Instant.now())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(inventoryServiceClient.getProductsById(eq(token), any())).thenReturn(Mono.just(List.of(product)));
        when(discountService.apply(any(), any())).thenAnswer(inv -> Mono.just(inv.getArgument(1)));
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(savedOrder));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(OrderItem.builder().build()));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());
        when(orderCommandProducer.send(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(orderService.createOrder(correlationId, token, request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getTransactionId()).isNotNull();
                })
                .verifyComplete();

        verify(orderCommandProducer).send(eq(AppConstant.TOPICS.STOCK_RESERVE_REQUESTED), any(), any());
    }

    @Test
    @DisplayName("createOrder - inventory service unavailable should error")
    void createOrder_inventoryUnavailable() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId("p1").quantity(1).build()))
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(inventoryServiceClient.getProductsById(eq(token), any())).thenReturn(Mono.empty());

        StepVerifier.create(orderService.createOrder(correlationId, token, request))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.INVENTORY_SERVICE_UNAVAILABLE)
                .verify();
    }

    @Test
    @DisplayName("getStatusOrder - happy path should return order status")
    void getStatusOrder_happyPath() {
        String transactionId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .transactionId(transactionId)
                .correlationId(correlationId)
                .userId("user-123")
                .orderStatus(AppConstant.ORDER_STATUS.WAITING_PAYMENT.name())
                .totalAmount(100.0)
                .createdDate(Instant.now())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderRepository.findByTransactionId(transactionId)).thenReturn(Mono.just(order));

        StepVerifier.create(orderService.getStatusOrder(token, transactionId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getOrderStatus()).isEqualTo("WAITING_PAYMENT");
                    assertThat(response.getBody().getTransactionId()).isEqualTo(transactionId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getStatusOrder - unauthorized user should error")
    void getStatusOrder_unauthorizedUser() {
        String transactionId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .transactionId(transactionId)
                .userId("different-user")
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderRepository.findByTransactionId(transactionId)).thenReturn(Mono.just(order));

        StepVerifier.create(orderService.getStatusOrder(token, transactionId))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.USER_UNAUTHORIZED)
                .verify();
    }

    @Test
    @DisplayName("getStatusOrder - transaction not found should error")
    void getStatusOrder_notFound() {
        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderRepository.findByTransactionId("non-existent")).thenReturn(Mono.empty());

        StepVerifier.create(orderService.getStatusOrder(token, "non-existent"))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.TRANSACTION_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("getUserOrders - should return list of orders for user")
    void getUserOrders_happyPath() {
        Order order = Order.builder()
                .transactionId(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .orderStatus(AppConstant.ORDER_STATUS.COMPLETED.name())
                .totalAmount(200.0)
                .createdDate(Instant.now())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderRepository.findAllByUserId("user-123")).thenReturn(Flux.just(order));

        StepVerifier.create(orderService.getUserOrders(token))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).hasSize(1);
                    assertThat(response.getBody().get(0).getOrderStatus()).isEqualTo("COMPLETED");
                })
                .verifyComplete();
    }
}
