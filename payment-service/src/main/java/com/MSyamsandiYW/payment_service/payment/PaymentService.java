package com.MSyamsandiYW.payment_service.payment;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.request.WebhookCallbackRequest;
import com.MSyamsandiYW.payment_service.payment.response.CreatePaymentResponse;
import com.MSyamsandiYW.payment_service.payment.response.GetPaymentResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface PaymentService {

    Mono<ResponseEntity<CreatePaymentResponse>> createPayment(CreatePaymentRequest request, String token);

    Mono<Void> webhookCallbackPaymentMethod(WebhookCallbackRequest request, HttpHeaders headers);

    Mono<Void> refundPayment(PaymentCommand request);

    Mono<ResponseEntity<List<GetPaymentResponse>>> getPaymentsByUser(String token);

    Mono<ResponseEntity<GetPaymentResponse>> getPaymentStatus(String transactionId, String token);
}
