package com.MSyamsandiYW.payment_service.payment.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.payment_service.client.OrderServiceClient;
import com.MSyamsandiYW.payment_service.client.response.GetOrderStatusResponse;
import com.MSyamsandiYW.payment_service.kafka.PaymentEventProducer;
import com.MSyamsandiYW.payment_service.kafka.event.DlqEventPayload;
import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import com.MSyamsandiYW.payment_service.kafka.event.PaymentEventPayload;
import com.MSyamsandiYW.payment_service.outbox.Outbox;
import com.MSyamsandiYW.payment_service.outbox.OutboxService;
import com.MSyamsandiYW.payment_service.payment.Payment;
import com.MSyamsandiYW.payment_service.payment.PaymentRepository;
import com.MSyamsandiYW.payment_service.payment.PaymentService;
import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.request.WebhookCallbackRequest;
import com.MSyamsandiYW.payment_service.payment.response.CreatePaymentResponse;
import com.MSyamsandiYW.payment_service.payment.response.GetPaymentResponse;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedgerService;
import com.MSyamsandiYW.payment_service.properties.AppConstant;
import com.MSyamsandiYW.payment_service.properties.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.MSyamsandiYW.payment_service.properties.AppConstant.PAYMENT_STATUS.*;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.*;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.PAYMENT_SUCCESS;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.REFUND_SUCCESS;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final PaymentEventProducer paymentEventProducer;
    private final JwtService jwtService;
    private final OrderServiceClient orderServiceClient;
    private final AppProperties appProperties;
    private final TransactionalOperator transactionalOperator;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    // Defines which current statuses are allowed to transition TO a given target status (same pattern as order-service)
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            PAID.name(), Set.of(AppConstant.PAYMENT_STATUS.PENDING.name()),
            FAILED.name(), Set.of(AppConstant.PAYMENT_STATUS.PENDING.name()),
            REFUNDED.name(), Set.of(PAID.name(), REFUND_FAILED.name()),
            REFUND_FAILED.name(), Set.of(PAID.name(), CANCELLED.name(), FAILED.name())
    );

    private record PaymentContext(Claims claims, GetOrderStatusResponse order, Payment payment) {
    }

    // payment status -> which topic the event routes to (aggregate_type) + event name (outbox metadata)
    private record EventRoute(String topic, String eventType) {
    }

    private static final Map<String, EventRoute> STATUS_TO_EVENT = Map.of(
            PENDING.name(), new EventRoute(PAYMENT_INITIATED, "PAYMENT_INITIATED"),
            PAID.name(), new EventRoute(PAYMENT_COMPLETED, "PAYMENT_COMPLETED"),
            FAILED.name(), new EventRoute(PAYMENT_FAILED, "PAYMENT_FAILED"),
            REFUNDED.name(), new EventRoute(ORDER_REFUND_COMPLETED, "ORDER_REFUND_COMPLETED"),
            REFUND_FAILED.name(), new EventRoute(ORDER_REFUND_FAILED, "ORDER_REFUND_FAILED")
    );

    @Override
    public Mono<ResponseEntity<CreatePaymentResponse>> createPayment(CreatePaymentRequest request, String token, String correlationId) {
        log.info("Creating payment for transactionId: {}", request.getTransactionId());

        return Mono.zip(jwtService.extractClaims(token),
                        orderServiceClient.getStatusOrder(request.getTransactionId(), token, correlationId).switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ORDER_SERVICE_UNAVAILABLE)))
                )
                // validate user order
                .flatMap(tuple -> validateUserOrder(tuple.getT2(), request).thenReturn(new PaymentContext(tuple.getT1(), tuple.getT2(), null)))
                // validate existing payment
                .flatMap(ctx -> validateCurrentPayment(request.getTransactionId()).thenReturn(ctx))
                // build payment
                .map(ctx -> new PaymentContext(ctx.claims, ctx.order, paymentBuilder(ctx.claims().get("userId").toString(), request, ctx.order())))
                // save payment,ledger,outbox in one transactional
                .flatMap(ctx -> paymentRepository.save(ctx.payment)
                        // save payment ledger
                        .flatMap(payment -> paymentLedgerService.recordEventPayment(payment).thenReturn(payment))
                        // build outbox — uses the saved payment (id is generated on insert)
                        .flatMap(payment -> insertOutbox(buildPayload(payment), PAYMENT_INITIATED, "PAYMENT_INITIATED").thenReturn(payment))
                        // flag as transactional
                        .as(transactionalOperator::transactional)
                )
                .doOnNext(payment -> log.info("Payment created successfully for transactionId: {}, status: {}", payment.getTransactionId(), payment.getStatus()))
                // build response
                .map(payment -> ResponseEntity.ok().body(buildResponse(payment, request.getPaymentMethod())));
    }


    @Override
    public Mono<Void> webhookCallbackPaymentMethod(WebhookCallbackRequest request, HttpHeaders headers) {
        log.info("Received webhook callback for paymentId: {}, status: {}", request.getPaymentId(), request.getPaymentStatus());

        return paymentRepository.findById(UUID.fromString(request.getPaymentId()))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                // guards: skip stale webhooks, handle silent-refund + DLQ edge cases — empty Mono means stop processing
                .flatMap(payment -> applyWebhookGuards(payment, request, headers))
                // update status payment
                .flatMap(payment -> updatePaymentEntity(payment, request)
                        // save payment ledger
                        .flatMap(updated -> paymentLedgerService.recordEventPayment(updated).thenReturn(updated))
                        // insert to outbox — topic + eventName resolved from one lookup
                        .flatMap(updated -> {
                            EventRoute route = STATUS_TO_EVENT.get(updated.getStatus());
                            if (route == null) {
                                return Mono.<Void>error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));
                            }
                            return insertOutbox(buildPayload(updated), route.topic(), route.eventType());
                        })
                        // flag as transactional
                        .as(transactionalOperator::transactional)
                        .thenReturn(payment)
                )
                .then();
    }

    private Mono<Payment> applyWebhookGuards(Payment payment, WebhookCallbackRequest request, HttpHeaders headers) {
        String currentStatus = payment.getStatus();
        String webhookStatus = request.getPaymentStatus();

        // === PAYMENT_SUCCESS webhook ===
        if (webhookStatus.equalsIgnoreCase(PAYMENT_SUCCESS.name())) {
            // CANCELLED + SUCCESS → silent refund (don't produce event)
            if (currentStatus.equalsIgnoreCase(CANCELLED.name())) {
                log.info("Payment {} was CANCELLED but provider charged — triggering silent refund", payment.getId());
                return refundPayment(payment.getId()).then(Mono.empty());
            }
            // FAILED (expired) + SUCCESS -> silent refund(scheduler already expired it, but provider charge it)
            if (currentStatus.equalsIgnoreCase(FAILED.name())) {
                log.info("Payment {} was FAILED (expired) but provider charged — triggering silent refund", payment.getId());
                return refundPayment(payment.getId()).then(Mono.empty());
            }

            // Only PENDING is valid for PAYMENT_SUCCESS
            if (!currentStatus.equalsIgnoreCase(AppConstant.PAYMENT_STATUS.PENDING.name())) {
                log.warn("Ignoring PAYMENT_SUCCESS webhook for paymentId: {} — current status: {}", payment.getId(), currentStatus);
                return Mono.empty();
            }
        }

        // === PAYMENT_FAILED webhook ===
        if (webhookStatus.equalsIgnoreCase(AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.PAYMENT_FAILED.name())) {
            // Only PENDING is valid for PAYMENT_FAILED
            if (!currentStatus.equalsIgnoreCase(AppConstant.PAYMENT_STATUS.PENDING.name())) {
                log.warn("Ignoring PAYMENT_FAILED webhook for paymentId: {} — current status: {}", payment.getId(), currentStatus);
                return Mono.empty();
            }
        }

        // === REFUND_SUCCESS webhook ===
        if (webhookStatus.equalsIgnoreCase(REFUND_SUCCESS.name())) {
            // CANCELLED or FAILED (expired) + REFUND_SUCCESS → mark REFUNDED, DON'T produce event (silent refund completion)
            if (Set.of(CANCELLED.name(), FAILED.name()).contains(currentStatus)) {
                log.info("Silent refund completed for {} paymentId: {}", currentStatus, payment.getId());
                // CAS: only mark REFUNDED if still CANCELLED/FAILED — prevents duplicate ledger on webhook redelivery
                return updatePaymentStatus(payment, REFUNDED.name(), null, null, Set.of(CANCELLED.name(), FAILED.name()))
                        .flatMap(paymentLedgerService::recordEventPayment)
                        .then(Mono.empty());
            }
            // Only SUCCESS or REFUND_FAILED proceed to normal flow — orchestrator is waiting for ORDER_REFUND_COMPLETED
            if (!Set.of(PAID.name(), REFUND_FAILED.name()).contains(currentStatus)) {
                log.warn("Ignoring REFUND_SUCCESS webhook for paymentId: {} — current status: {}", payment.getId(), currentStatus);
                return Mono.empty();
            }
        }

        // === REFUND_FAILED webhook ===
        if (webhookStatus.equalsIgnoreCase(AppConstant.ORDER_STATUS.REFUND_FAILED.name())) {
            // Only CANCELLED, FAILED (expired), or SUCCESS are valid for REFUND_FAILED
            if (!Set.of(CANCELLED.name(), PAID.name(), FAILED.name()).contains(currentStatus)) {
                log.warn("Ignoring REFUND_FAILED webhook for paymentId: {} — current status: {}", payment.getId(), currentStatus);
                return Mono.empty();
            }
            // send to dlq for refund failed to need manual intervention, but continue normal flow
            log.warn("Refund failed for paymentId: {} — sending to DLQ for manual intervention", payment.getId());
            return sendToDlq(request, headers).thenReturn(payment);
        }

        return Mono.just(payment);
    }


    @Override
    public Mono<Void> refundPayment(PaymentCommand request) {
        log.info("Processing refund for transactionId: {}", request.getTransactionId());

        return paymentRepository.findById(UUID.fromString(request.getPaymentId()))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                .flatMap(payment -> {
                    // Build request body
                    // Request refund to third party
                    // and wait webhook callback from payment method provider
                    return Mono.empty();
                })
                ;
    }

    @Override
    public Mono<ResponseEntity<List<GetPaymentResponse>>> getPaymentsByUser(String token) {
        log.info("Fetching payments for user");

        return jwtService.extractClaims(token)
                // find payments by user id
                .flatMap(claims -> paymentRepository.findByUserId(claims.get("userId").toString()).collectList())
                // mapping to response
                .map(payments -> {
                    List<GetPaymentResponse> response = payments.stream().map(p -> GetPaymentResponse.builder()
                                    .transactionId(p.getTransactionId())
                                    .paymentMethod(p.getPaymentMethod())
                                    .amount(p.getAmount())
                                    .status(p.getStatus())
                                    .createdAt(p.getCreatedAt())
                                    .build())
                            .toList();

                    return ResponseEntity.ok().body(response);
                });
    }

    @Override
    public Mono<ResponseEntity<GetPaymentResponse>> getPaymentStatus(String transactionId, String token) {

        //extract token and get payment by transactionId
        return Mono.zip(
                        jwtService.extractClaims(token),
                        paymentRepository.findFirstByTransactionIdOrderByCreatedAtDesc(transactionId)
                                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))))
                .flatMap(tuple -> {
                    Claims claims = tuple.getT1();
                    Payment payment = tuple.getT2();

                    // validate is user authorized
                    String userId = claims.get("userId").toString();
                    if (userId == null || !userId.equals(payment.getUserId())) {
                        return Mono.error(new BusinessException(ErrorCode.USER_UNAUTHORIZED));
                    }
                    return Mono.just(payment);
                })

                // mapping response to client
                .flatMap(payment -> Mono.just(ResponseEntity.ok().body(
                        GetPaymentResponse.builder()
                                .transactionId(payment.getTransactionId())
                                .paymentMethod(payment.getPaymentMethod())
                                .amount(payment.getAmount())
                                .status(payment.getStatus())
                                .createdAt(payment.getCreatedAt())
                                .build()
                )))
                ;
    }

    private Mono<Payment> updatePaymentEntity(Payment payment, WebhookCallbackRequest request) {
        log.debug("Updating payment entity for transactionId: {}, new status: {}", payment.getTransactionId(), request.getPaymentStatus());

        if (PAYMENT_SUCCESS.name().equalsIgnoreCase(request.getPaymentStatus())) {
            return updatePaymentStatus(payment, PAID.name(), null, null);
        } else if (AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.PAYMENT_FAILED.name().equalsIgnoreCase(request.getPaymentStatus())) {
            return updatePaymentStatus(payment, FAILED.name(), request.getFailureCode(), request.getFailureMessage());
        } else if (REFUND_SUCCESS.name().equalsIgnoreCase(request.getPaymentStatus())) {
            return updatePaymentStatus(payment, REFUNDED.name(), null, null);
        } else if (AppConstant.ORDER_STATUS.REFUND_FAILED.name().equalsIgnoreCase(request.getPaymentStatus())) {
            return updatePaymentStatus(payment, REFUND_FAILED.name(), request.getFailureCode(), request.getFailureMessage());
        }
        return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));
    }

    private Mono<Void> validateCurrentPayment(String transactionId) {
        // find active current payment
        return paymentRepository.findFirstByTransactionIdAndStatus(transactionId, AppConstant.PAYMENT_STATUS.PENDING.name())
                // cancel current payment if exist
                .flatMap(existingPayment -> paymentRepository.updatePendingStatusPayment(existingPayment.getId(), CANCELLED.name(), ErrorCode.PAYMENT_CANCELLED.getCode(), ErrorCode.PAYMENT_CANCELLED.getDefaultMessage())
                        .filter(rows -> rows > 0)
                        .flatMap(__ -> {
                            log.info("Cancelled existing PENDING payment: {}", existingPayment.getId());
                            // call third party provide to cancel current payment
                            return Mono.empty();
                        })
                )
                .then();
    }

    private Mono<Void> validateUserOrder(GetOrderStatusResponse order, CreatePaymentRequest request) {
        // validate payment method
        if (appProperties.getPaymentMethodUrlMap().get(request.getPaymentMethod()) == null) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD));
        }

        // validate order status
        if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.PAID.name())) {
            return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_PAID));
        }
        if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.COMPLETED.name())) {
            return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED));
        }
        if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.REFUNDED.name())) {
            return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_REFUNDED));
        }
        if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.OUT_OF_STOCK.name())) {
            return Mono.error(new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK));
        }
        if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.EXPIRED.name())) {
            return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_EXPIRED));
        }
        if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.REFUND_FAILED.name())) {
            return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED));
        }
        return Mono.empty();
    }


    private Mono<Void> refundPayment(UUID paymentId) {
        log.info("Processing refund for paymentId: {}", paymentId);

        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                .flatMap(payment -> {
                    // Build request body
                    // Request refund to third party payment provider
                    // and wait webhook callback from payment method provider
                    return Mono.empty();
                });
    }

    private Mono<Void> sendToDlq(WebhookCallbackRequest request, HttpHeaders headers) {
        DlqEventPayload payload = DlqEventPayload.builder()
                .originalTopic("webhook-callback")
                .originalKey(request.getPaymentId())
                .originalPayload(request)
                .errorMessage("Refund failed - manual intervention required")
                .timestamp(Instant.now())
                .headers(headers.toSingleValueMap())
                .build();
        return paymentEventProducer.send(PAYMENT_DLQ, request.getPaymentId(), payload);
    }

    private Mono<Payment> updatePaymentStatus(Payment payment, String status, String failureCode, String failureMessage) {
        //update with cas for atomic — only allowed source statuses can transition
        return updatePaymentStatus(payment, status, failureCode, failureMessage, ALLOWED_TRANSITIONS.get(status));
    }

    private Mono<Payment> updatePaymentStatus(Payment payment, String status, String failureCode, String failureMessage, Set<String> allowedStatuses) {
        //update with cas for atomic
        return paymentRepository.updateStatusPayment(payment.getId(), status, failureCode, failureMessage, allowedStatuses)
                .filter(rows -> rows > 0)
                .map(__ -> {
                    log.info("Payment status updated to {} - transactionId: {}, correlationId: {}",
                            status, payment.getTransactionId(), payment.getCorrelationId());
                    // this payment in memory is for ledger
                    payment.setStatus(status);
                    payment.setFailureCode(failureCode);
                    payment.setFailureMessage(failureMessage);
                    return payment;
                });
    }

    private Mono<Void> insertOutbox(PaymentEventPayload payload, String topic, String eventName) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> Outbox.builder()
                        .aggregateId(UUID.randomUUID().toString())
                        .aggregateType(topic)
                        .eventType(eventName)
                        .payload(json)
                        .build())
                .flatMap(outboxService::save);

    }

    private PaymentEventPayload buildPayload(Payment payment) {
        return PaymentEventPayload.builder()
                .paymentId(payment.getId().toString())
                .correlationId(payment.getCorrelationId())
                .transactionId(payment.getTransactionId())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .build();
    }

    private Payment paymentBuilder(String userId, CreatePaymentRequest request, GetOrderStatusResponse order) {
        return Payment.builder()
                .userId(userId)
                .transactionId(request.getTransactionId())
                .correlationId(order.getCorrelationId())
                .paymentMethod(request.getPaymentMethod())
                .amount(order.getTotalAmount())
                .status(AppConstant.PAYMENT_STATUS.PENDING.name())
                .createdBy("PAYMENT_SERVICE")
                .createdAt(Instant.now())
                .build();
    }

    private CreatePaymentResponse buildResponse(Payment payment, String paymentMethod) {
        return CreatePaymentResponse.builder()
                .transactionId(payment.getTransactionId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .urlPayment(appProperties.getPaymentMethodUrlMap().get(paymentMethod))
                // TODO: Remove paymentId — testing only
                .paymentId(payment.getId().toString())
                .build();
    }
}
