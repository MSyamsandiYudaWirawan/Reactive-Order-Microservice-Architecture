package com.MSyamsandiYW.payment_service.payment.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.payment_service.client.OrderServiceClient;
import com.MSyamsandiYW.payment_service.client.response.GetOrderStatusResponse;
import com.MSyamsandiYW.payment_service.kafka.PaymentEventProducer;
import com.MSyamsandiYW.payment_service.payment.Payment;
import com.MSyamsandiYW.payment_service.payment.PaymentRepository;
import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.request.WebhookCallbackRequest;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedgerService;
import com.MSyamsandiYW.payment_service.properties.AppConstant;
import com.MSyamsandiYW.payment_service.properties.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentLedgerService paymentLedgerService;
    @Mock
    private PaymentEventProducer paymentEventProducer;
    @Mock
    private JwtService jwtService;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Claims claims;
    private String token;
    private String transactionId;

    @BeforeEach
    void setUp() {
        token = "test-token";
        transactionId = UUID.randomUUID().toString();

        Map<String, Object> claimsMap = Map.of(
                "userId", "user-123",
                "userEmail", "test@test.com"
        );
        claims = new DefaultClaims(claimsMap);
    }

    @Test
    @DisplayName("createPayment - happy path should create payment and return URL")
    void createPayment_happyPath() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .transactionId(transactionId)
                .paymentMethod("BCA_VA")
                .build();

        GetOrderStatusResponse orderStatus = GetOrderStatusResponse.builder()
                .orderStatus(AppConstant.ORDER_STATUS.WAITING_PAYMENT.name())
                .correlationId(UUID.randomUUID().toString())
                .totalAmount(100.0)
                .build();

        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .userId("user-123")
                .transactionId(transactionId)
                .correlationId(orderStatus.getCorrelationId())
                .paymentMethod("BCA_VA")
                .amount(100.0)
                .status(AppConstant.PAYMENT_STATUS.PENDING.name())
                .createdDate(Instant.now())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderServiceClient.getStatusOrder(transactionId, token)).thenReturn(Mono.just(orderStatus));
        when(appProperties.getPaymentMethodUrlMap()).thenReturn(Map.of("BCA_VA", "https://payment.example.com/bca"));
        when(paymentRepository.findFirstByTransactionIdAndStatus(transactionId, AppConstant.PAYMENT_STATUS.PENDING.name()))
                .thenReturn(Mono.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(Mono.just(savedPayment));
        when(paymentLedgerService.recordEventPayment(any())).thenReturn(Mono.empty());
        when(paymentEventProducer.send(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.createPayment(request, token))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getTransactionId()).isEqualTo(transactionId);
                    assertThat(response.getBody().getUrlPayment()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("createPayment - order already paid should error")
    void createPayment_orderAlreadyPaid() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .transactionId(transactionId)
                .paymentMethod("BCA_VA")
                .build();

        GetOrderStatusResponse orderStatus = GetOrderStatusResponse.builder()
                .orderStatus(AppConstant.ORDER_STATUS.PAID.name())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderServiceClient.getStatusOrder(transactionId, token)).thenReturn(Mono.just(orderStatus));
        when(appProperties.getPaymentMethodUrlMap()).thenReturn(Map.of("BCA_VA", "https://url"));

        StepVerifier.create(paymentService.createPayment(request, token))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.ORDER_ALREADY_PAID)
                .verify();
    }

    @Test
    @DisplayName("createPayment - invalid payment method should error")
    void createPayment_invalidPaymentMethod() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .transactionId(transactionId)
                .paymentMethod("INVALID")
                .build();

        GetOrderStatusResponse orderStatus = GetOrderStatusResponse.builder()
                .orderStatus(AppConstant.ORDER_STATUS.WAITING_PAYMENT.name())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(orderServiceClient.getStatusOrder(transactionId, token)).thenReturn(Mono.just(orderStatus));
        when(appProperties.getPaymentMethodUrlMap()).thenReturn(Map.of("BCA_VA", "https://url"));

        StepVerifier.create(paymentService.createPayment(request, token))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_PAYMENT_METHOD)
                .verify();
    }

    @Test
    @DisplayName("webhookCallback - PAYMENT_SUCCESS on PENDING payment should mark SUCCESS")
    void webhookCallback_paymentSuccess() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .transactionId(transactionId)
                .correlationId(UUID.randomUUID().toString())
                .status(AppConstant.PAYMENT_STATUS.PENDING.name())
                .build();

        WebhookCallbackRequest request = WebhookCallbackRequest.builder()
                .paymentId(paymentId.toString())
                .paymentStatus(AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.PAYMENT_SUCCESS.name())
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentLedgerService.recordEventPayment(any())).thenReturn(Mono.empty());
        when(paymentEventProducer.send(eq(AppConstant.TOPICS.PAYMENT_COMPLETED), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(paymentService.webhookCallbackPaymentMethod(request, new HttpHeaders()))
                .verifyComplete();

        verify(paymentEventProducer).send(eq(AppConstant.TOPICS.PAYMENT_COMPLETED), any(), any());
    }

    @Test
    @DisplayName("webhookCallback - payment not found should error")
    void webhookCallback_paymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        WebhookCallbackRequest request = WebhookCallbackRequest.builder()
                .paymentId(paymentId.toString())
                .paymentStatus(AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.PAYMENT_SUCCESS.name())
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.webhookCallbackPaymentMethod(request, new HttpHeaders()))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.PAYMENT_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("getPaymentsByUser - should return payments for authenticated user")
    void getPaymentsByUser_happyPath() {
        Payment payment = Payment.builder()
                .transactionId(transactionId)
                .paymentMethod("BCA_VA")
                .amount(50.0)
                .status(AppConstant.PAYMENT_STATUS.SUCCESS.name())
                .createdDate(Instant.now())
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(paymentRepository.findByUserId("user-123")).thenReturn(Flux.just(payment));

        StepVerifier.create(paymentService.getPaymentsByUser(token))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).hasSize(1);
                    assertThat(response.getBody().get(0).getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getPaymentStatus - unauthorized user should error")
    void getPaymentStatus_unauthorizedUser() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .userId("different-user")
                .build();

        when(jwtService.extractClaims(token)).thenReturn(Mono.just(claims));
        when(paymentRepository.findFirstByTransactionIdOrderByCreatedDateDesc(transactionId))
                .thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.getPaymentStatus(transactionId, token))
                .expectErrorMatches(e -> e instanceof BusinessException
                        && ((BusinessException) e).getErrorCode() == ErrorCode.USER_UNAUTHORIZED)
                .verify();
    }
}
