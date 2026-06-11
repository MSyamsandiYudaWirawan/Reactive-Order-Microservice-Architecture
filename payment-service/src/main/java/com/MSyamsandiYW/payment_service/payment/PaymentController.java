package com.MSyamsandiYW.payment_service.payment;

import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.response.CallbackPaymentMethodResponse;
import com.MSyamsandiYW.payment_service.payment.response.CreatePaymentResponse;
import com.MSyamsandiYW.payment_service.payment.response.GetPaymentByUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment", description = "Payment API")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/webhook/callback")
    @ResponseStatus(code = OK)
    Mono<Void> webhookCallbackPaymentMethod(
            @RequestBody @Valid CallbackPaymentMethodResponse response
    ) {
        return paymentService.webhookCallbackPaymentMethod(response);
    }

    @PostMapping
    @ResponseStatus(code = OK)
    public Mono<ResponseEntity<CreatePaymentResponse>> createPayment(
            @RequestBody @Valid CreatePaymentRequest request,
            @RequestHeader("Authorization") String token
    ) {
        return paymentService.createPayment(request, token);
    }

    @GetMapping("/list")
    @ResponseStatus(code = OK)
    public Mono<ResponseEntity<List<GetPaymentByUser>>> getPaymentByUserId(
            @RequestHeader("Authorization") String token
    ) {
        return paymentService.getPaymentsUser(token);
    }
}
