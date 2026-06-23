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
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.MSyamsandiYW.payment_service.properties.AppConstant.ORDER_STATUS.*;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.PAYMENT_STATUS.*;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.PAYMENT_STATUS.REFUNDED;
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

    @Override
    public Mono<ResponseEntity<CreatePaymentResponse>> createPayment(CreatePaymentRequest request, String token) {
        log.info("Creating payment for transactionId: {}", request.getTransactionId());

        return Mono.zip(jwtService.extractClaims(token),
                        orderServiceClient.getStatusOrder(request.getTransactionId(), token).switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ORDER_SERVICE_UNAVAILABLE)))
                )
                .flatMap(tuple -> {
                    Claims claims = tuple.getT1();
                    GetOrderStatusResponse order = tuple.getT2();

                    // validate payment method
                    if (appProperties.getPaymentMethodUrlMap().get(request.getPaymentMethod()) == null) {
                        return Mono.error(new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD));
                    }

                    // validate order status
                    if (order.getOrderStatus().equalsIgnoreCase(PAID.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_PAID));
                    }
                    if (order.getOrderStatus().equalsIgnoreCase(COMPLETED.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED));
                    }
                    if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.REFUNDED.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_REFUNDED));
                    }
                    if (order.getOrderStatus().equalsIgnoreCase(OUT_OF_STOCK.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK));
                    }
                    if (order.getOrderStatus().equalsIgnoreCase(EXPIRED.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_EXPIRED));
                    }
                    if (order.getOrderStatus().equalsIgnoreCase(AppConstant.ORDER_STATUS.REFUND_FAILED.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED));
                    }

                    // build payment entity
                    return Mono.just(Payment.builder()
                            .userId(claims.get("userId").toString())
                            .transactionId(request.getTransactionId())
                            .correlationId(order.getCorrelationId())
                            .paymentMethod(request.getPaymentMethod())
                            .amount(order.getTotalAmount())
                            .status(AppConstant.PAYMENT_STATUS.PENDING.name())
                            .createdBy("PAYMENT_SERVICE")
                            .createdDate(Instant.now())
                            .build());
                })
                // validate current active payment
                .flatMap(payment -> validateCurrentPayment(payment, request.getTransactionId())
                )
                // save payment
                .flatMap(paymentRepository::save)
                .doOnNext(payment -> log.info("Payment created successfully for transactionId: {}, status: {}", payment.getTransactionId(), payment.getStatus()))
                // save payment ledger
                .flatMap(payment -> paymentLedgerService.recordEventPayment(payment).thenReturn(payment))
                .flatMap(payment -> produceEventPayment(payment).thenReturn(payment))
                // build response
                .flatMap(payment ->
                        Mono.just(ResponseEntity.ok().body(
                                CreatePaymentResponse.builder()
                                        .transactionId(payment.getTransactionId())
                                        .amount(payment.getAmount())
                                        .paymentMethod(payment.getPaymentMethod())
                                        .urlPayment(appProperties.getPaymentMethodUrlMap().get(request.getPaymentMethod()))
                                        // TODO: Remove paymentId — testing only
                                        .paymentId(payment.getId().toString())
                                        .build()
                        ))
                );
    }

    @Override
    public Mono<Void> webhookCallbackPaymentMethod(WebhookCallbackRequest request, HttpHeaders headers) {
        log.info("Received webhook callback for paymentId: {}, status: {}", request.getPaymentId(), request.getPaymentStatus());

        return paymentRepository.findById(UUID.fromString(request.getPaymentId()))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                .flatMap(payment -> validatePayment(payment, request, headers))
                // update status payment
                .flatMap(payment -> updatePaymentEntity(payment, request))
                // save payment
                .flatMap(paymentRepository::save)
                // save payment ledger
                .flatMap(payment -> paymentLedgerService.recordEventPayment(payment).thenReturn(payment))
                // produce payment event and will be consumed by orchestrator-service or order-service
                .flatMap(this::produceEventPayment)
                .then();
    }

    private Mono<Payment> validatePayment(Payment payment, WebhookCallbackRequest request, HttpHeaders headers) {
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
            if(currentStatus.equalsIgnoreCase(FAILED.name())){
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
            // CANCELLED + REFUND_SUCCESS → mark REFUNDED, DON'T produce event (silent refund completion)
            if (currentStatus.equalsIgnoreCase(CANCELLED.name())) {
                log.info("Silent refund completed for CANCELLED paymentId: {}", payment.getId());
                payment.setStatus(REFUNDED.name());
                payment.setUpdatedBy("PAYMENT_SERVICE");
                payment.setLastModifiedDate(Instant.now());
                return paymentRepository.save(payment)
                        .flatMap(saved -> paymentLedgerService.recordEventPayment(saved).thenReturn(saved))
                        .then(Mono.empty());
            }
            // Only SUCCESS or REFUND_FAILED are valid for REFUND_SUCCESS
            if (!Set.of(SUCCESS.name(), AppConstant.PAYMENT_STATUS.REFUND_FAILED.name()).contains(currentStatus)) {
                log.warn("Ignoring REFUND_SUCCESS webhook for paymentId: {} — current status: {}", payment.getId(), currentStatus);
                return Mono.empty();
            }
        }

        // === REFUND_FAILED webhook ===
        if (webhookStatus.equalsIgnoreCase(AppConstant.ORDER_STATUS.REFUND_FAILED.name())) {
            // Only CANCELLED or SUCCESS are valid for REFUND_FAILED
            if (!Set.of(CANCELLED.name(), SUCCESS.name()).contains(currentStatus)) {
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
                                    .createdDate(p.getCreatedDate())
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
                        paymentRepository.findFirstByTransactionIdOrderByCreatedDateDesc(transactionId)
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
                                .createdDate(payment.getCreatedDate())
                                .build()
                )))
                ;
    }

    private Mono<Payment> updatePaymentEntity(Payment payment, WebhookCallbackRequest request) {
        log.debug("Updating payment entity for transactionId: {}, new status: {}", payment.getTransactionId(), request.getPaymentStatus());

        if (PAYMENT_SUCCESS.name().equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(SUCCESS.name());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(Instant.now());
            return Mono.just(payment);
        } else if (AppConstant.WEBHOOK_CALLBACK_PAYMENT_STATUS.PAYMENT_FAILED.name().equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(FAILED.name());
            payment.setFailureCode(request.getFailureCode());
            payment.setFailureMessage(request.getFailureMessage());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(Instant.now());
            return Mono.just(payment);
        } else if (REFUND_SUCCESS.name().equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(REFUNDED.name());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(Instant.now());
            return Mono.just(payment);
        } else if (AppConstant.ORDER_STATUS.REFUND_FAILED.name().equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(AppConstant.PAYMENT_STATUS.REFUND_FAILED.name());
            payment.setFailureCode(request.getFailureCode());
            payment.setFailureMessage(request.getFailureMessage());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(Instant.now());
            return Mono.just(payment);
        }
        return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));
    }

    private Mono<Void> produceEventPayment(Payment payment) {
        log.info("Producing payment event for transactionId: {}, status: {}", payment.getTransactionId(), payment.getStatus());

        PaymentEventPayload payload = PaymentEventPayload.builder()
                .paymentId(payment.getId().toString())
                .transactionId(payment.getTransactionId())
                .correlationId(payment.getCorrelationId())
                .build();


        if (payment.getStatus().equalsIgnoreCase(SUCCESS.name())) {
            return paymentEventProducer.send(PAYMENT_COMPLETED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(AppConstant.PAYMENT_STATUS.PENDING.name())) {
            return paymentEventProducer.send(PAYMENT_INITIATED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(FAILED.name())) {
            payload.setFailureCode(payment.getFailureCode());
            payload.setFailureMessage(payment.getFailureMessage());
            return paymentEventProducer.send(PAYMENT_FAILED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(REFUNDED.name())) {
            return paymentEventProducer.send(ORDER_REFUND_COMPLETED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(AppConstant.PAYMENT_STATUS.REFUND_FAILED.name())) {
            return paymentEventProducer.send(ORDER_REFUND_FAILED, UUID.randomUUID().toString(), payload);
        }
        return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));

    }

    private Mono<Payment> validateCurrentPayment(Payment newPayment, String transactionId) {
        // find active current payment
        return paymentRepository.findFirstByTransactionIdAndStatus(transactionId, AppConstant.PAYMENT_STATUS.PENDING.name())
                // cancel current payment if exist
                .flatMap(existingPayment -> { // only runs if PENDING found

                    // future: call third-party cancel API
                    // return paymentProviderClient.cancelPayment(existingPayment.getProviderRef())
                    //     .then(...)
                    existingPayment.setStatus(CANCELLED.name());
                    existingPayment.setUpdatedBy("PAYMENT_SERVICE");
                    existingPayment.setLastModifiedDate(Instant.now());
                    return paymentRepository.save(existingPayment);
                })
                .thenReturn(newPayment);  // always returns newPayment regardless
    }


    private Mono<Void> refundPayment(UUID paymentId) {
        log.info("Processing refund for paymentId: {}", paymentId);

        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                .flatMap(payment -> {
                    // Build request body
                    // Request refund to third party
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


}
