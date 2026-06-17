package com.MSyamsandiYW.payment_service.payment_ledger;

import com.MSyamsandiYW.payment_service.payment.Payment;
import reactor.core.publisher.Mono;

public interface PaymentLedgerService {

    Mono<Void> recordEventPayment(Payment payment);
}
