package com.MSyamsandiYW.payment_service.kafka;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import com.MSyamsandiYW.payment_service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCommandHandler {
    private final PaymentService paymentService;

    public Mono<Void> handleRefundPayment(PaymentCommand payload){
        return paymentService.refundPayment(payload);
    }

}
