package com.MSyamsandiYW.payment_service.payment_ledger.impl;

import com.MSyamsandiYW.payment_service.payment.Payment;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedger;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedgerRepository;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentLedgerServiceImpl implements PaymentLedgerService {
    private final PaymentLedgerRepository paymentLedgerRepository;

    @Override
    public Mono<Void> recordEventPayment(Payment payment) {
        PaymentLedger paymentLedger = PaymentLedger.builder()
                .paymentId(payment.getId().toString())
                .transactionId(payment.getTransactionId())
                .correlationId(payment.getCorrelationId())
                .eventType(payment.getStatus())
                .createdDate(payment.getCreatedDate())
                .build();
        return paymentLedgerRepository.save(paymentLedger).then();
    }
}
