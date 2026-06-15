package com.MSyamsandiYW.payment_service.payment;

import com.MSyamsandiYW.payment_service.payment.request.CreatePaymentRequest;
import com.MSyamsandiYW.payment_service.payment.request.WebhookCallbackRequest;
import com.MSyamsandiYW.payment_service.payment.response.CreatePaymentResponse;
import com.MSyamsandiYW.payment_service.payment.response.GetPaymentResponse;
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
            @RequestBody @Valid WebhookCallbackRequest request
    ) {
        return paymentService.webhookCallbackPaymentMethod(request);
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
    public Mono<ResponseEntity<List<GetPaymentResponse>>> getPaymentsByUser(
            @RequestHeader("Authorization") String token
    ) {
        return paymentService.getPaymentsByUser(token);
    }

    @GetMapping("/status/{transactionId}")
    @ResponseStatus(code = OK)
    public Mono<ResponseEntity<GetPaymentResponse>> getPaymentByTransactionId(
            @PathVariable("transactionId") String transactionId,
            @RequestHeader("Authorization") String token
    ){
        return paymentService.getPaymentStatus(transactionId, token);
    }
}
