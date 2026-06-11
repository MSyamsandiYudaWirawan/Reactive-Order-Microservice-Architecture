package com.MSyamsandiYW.payment_service.payment;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.response.CallbackPaymentMethodResponse;
import com.MSyamsandiYW.payment_service.payment.response.CreatePaymentResponse;
import com.MSyamsandiYW.payment_service.payment.response.GetPaymentByUser;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PaymentService {

    Mono<ResponseEntity<CreatePaymentResponse>> createPayment(CreatePaymentRequest request, String token);

    Mono<Void> webhookCallbackPaymentMethod(CallbackPaymentMethodResponse response);
    //consume kafka
    Mono<Void> refundPayment(PaymentCommand request);

    Mono<ResponseEntity<List<GetPaymentByUser>>> getPaymentsUser(String token);
}
