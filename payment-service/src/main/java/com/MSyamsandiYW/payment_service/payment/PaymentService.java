package com.MSyamsandiYW.payment_service.payment;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.request.WebhookCallbackRequest;
import com.MSyamsandiYW.payment_service.payment.response.CreatePaymentResponse;
import com.MSyamsandiYW.payment_service.payment.response.GetPaymentsResponse;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PaymentService {

    Mono<ResponseEntity<CreatePaymentResponse>> createPayment(CreatePaymentRequest request, String token);

    Mono<Void> webhookCallbackPaymentMethod(WebhookCallbackRequest request);

    Mono<Void> refundPayment(PaymentCommand request);

    Mono<ResponseEntity<List<GetPaymentsResponse>>> getPaymentsByUser(String token);
}
