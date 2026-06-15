package com.MSyamsandiYW.payment_service.payment.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.payment_service.client.OrderServiceClient;
import com.MSyamsandiYW.payment_service.client.response.GetOrderStatusResponse;
import com.MSyamsandiYW.payment_service.kafka.PaymentEventProducer;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static com.MSyamsandiYW.payment_service.properties.AppConstant.ORDER_STATUS.*;

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

        return Mono.zip(jwtService.extractClaims(token), orderServiceClient.getStatusOrder(request.getTransactionId(), token))
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
                    if (order.getOrderStatus().equalsIgnoreCase(FAILED.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED));
                    }
                    if (order.getOrderStatus().equalsIgnoreCase(REFUNDED.name())) {
                        return Mono.error(new BusinessException(ErrorCode.ORDER_ALREADY_REFUNDED));
                    }

                    // build payment entity
                    return Mono.just(Payment.builder()
                            .userId(claims.getId())
                            .transactionId(request.getTransactionId())
                            .correlationId(order.getCorrelationId())
                            .paymentMethod(request.getPaymentMethod())
                            .amount(order.getTotalAmount())
                            .status(AppConstant.PAYMENT_STATUS.PENDING.name())
                            .createdBy("PAYMENT_SERVICE")
                            .createdDate(ZonedDateTime.now())
                            .build());
                })
                // save payment
                .flatMap(paymentRepository::save)
                .doOnNext(payment -> log.info("Payment created successfully for transactionId: {}, status: {}", payment.getTransactionId(), payment.getStatus()))
                // save payment ledger
                .flatMap(payment -> paymentLedgerService.recordEventPayment(payment).thenReturn(payment))
                // build response
                .flatMap(payment ->
                        Mono.just(ResponseEntity.ok().body(
                                CreatePaymentResponse.builder()
                                        .transactionId(payment.getTransactionId())
                                        .amount(payment.getAmount())
                                        .paymentMethod(payment.getPaymentMethod())
                                        .urlPayment(appProperties.getPaymentMethodUrlMap().get(request.getPaymentMethod()))
                                        .build()
                        ))
                );
    }

    @Override
    public Mono<Void> webhookCallbackPaymentMethod(WebhookCallbackRequest request) {
        log.info("Received webhook callback for transactionId: {}, status: {}", request.getTransactionId(), request.getPaymentStatus());

        return paymentRepository.findByTransactionId(request.getTransactionId())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
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


    @Override
    public Mono<Void> refundPayment(PaymentCommand request) {
        log.info("Processing refund for transactionId: {}", request.getTransactionId());

        return paymentRepository.findByTransactionId(request.getTransactionId())
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
                .flatMap(claims -> paymentRepository.findByUserId(claims.getId()).collectList())
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
                        paymentRepository.findByTransactionId(transactionId)
                                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))))
                .flatMap(tuple -> {
                    Claims claims = tuple.getT1();
                    Payment payment = tuple.getT2();

                    // validate is user authorized
                    if (claims.getId() == null || !claims.getId().equals(payment.getUserId())) {
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

        if ("PAYMENT_SUCCESS".equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(AppConstant.PAYMENT_STATUS.SUCCESS.name());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(ZonedDateTime.now());
            return Mono.just(payment);
        } else if ("PAYMENT_FAILED".equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(AppConstant.PAYMENT_STATUS.FAILED.name());
            payment.setFailureCode(request.getFailureCode());
            payment.setFailureMessage(request.getFailureMessage());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(ZonedDateTime.now());
            return Mono.just(payment);
        }
        if ("REFUND_SUCCESS".equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(AppConstant.PAYMENT_STATUS.REFUNDED.name());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(ZonedDateTime.now());
            return Mono.just(payment);
        } else if ("REFUND_FAILED".equalsIgnoreCase(request.getPaymentStatus())) {
            payment.setStatus(AppConstant.PAYMENT_STATUS.REFUND_FAILED.name());
            payment.setFailureCode(request.getFailureCode());
            payment.setFailureMessage(request.getFailureMessage());
            payment.setUpdatedBy("PAYMENT_SERVICE");
            payment.setLastModifiedDate(ZonedDateTime.now());
            return Mono.just(payment);
        }
        return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));
    }

    private Mono<Void> produceEventPayment(Payment payment) {
        log.info("Producing payment event for transactionId: {}, status: {}", payment.getTransactionId(), payment.getStatus());

        PaymentEventPayload payload = PaymentEventPayload.builder()
                .transactionId(payment.getTransactionId())
                .correlationId(payment.getCorrelationId())
                .build();


        if (payment.getStatus().equalsIgnoreCase(AppConstant.PAYMENT_STATUS.SUCCESS.name())) {
            return paymentEventProducer.send(AppConstant.TOPICS.PAYMENT_COMPLETED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(AppConstant.PAYMENT_STATUS.FAILED.name())) {
            payload.setFailureCode(payment.getFailureCode());
            payload.setFailureMessage(payment.getFailureMessage());
            return paymentEventProducer.send(AppConstant.TOPICS.PAYMENT_FAILED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(AppConstant.PAYMENT_STATUS.REFUNDED.name())) {
            return paymentEventProducer.send(AppConstant.TOPICS.ORDER_REFUND_COMPLETED, UUID.randomUUID().toString(), payload);
        } else if (payment.getStatus().equalsIgnoreCase(AppConstant.PAYMENT_STATUS.REFUND_FAILED.name())) {
            return paymentEventProducer.send(AppConstant.TOPICS.ORDER_REFUND_FAILED, UUID.randomUUID().toString(), payload);
        }
        return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));

    }
}
