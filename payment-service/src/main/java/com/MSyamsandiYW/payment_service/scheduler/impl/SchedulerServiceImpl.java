package com.MSyamsandiYW.payment_service.scheduler.impl;

import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.payment_service.kafka.PaymentEventProducer;
import com.MSyamsandiYW.payment_service.kafka.event.PaymentEventPayload;
import com.MSyamsandiYW.payment_service.payment.Payment;
import com.MSyamsandiYW.payment_service.payment.PaymentRepository;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedgerService;
import com.MSyamsandiYW.payment_service.properties.AppConstant;
import com.MSyamsandiYW.payment_service.properties.AppProperties;
import com.MSyamsandiYW.payment_service.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static com.MSyamsandiYW.payment_service.properties.AppConstant.PAYMENT_STATUS.FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerServiceImpl implements SchedulerService {

    private final AppProperties appProperties;
    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentLedgerService paymentLedgerService;

    @Override
    public Mono<Void> executeScheduler() {
        Instant cutoff = Instant.now().minusSeconds(appProperties.getPaymentExpirySeconds());
        log.info("Finding expired payment with cutoff: {}", cutoff);

        return paymentRepository.findAllExpiredPayments(cutoff)
                .flatMap(expiredPayment ->
                        // CAS update: only marks FAILED if still PENDING (prevents race with webhook)
                        paymentRepository.updateStatusPayment(expiredPayment.getId(), FAILED.name())
                                .filter(rowsUpdated -> rowsUpdated > 0)
                                .flatMap(__ -> {
                                    // sync in-memory object with DB state for accurate ledger recording
                                    expiredPayment.setStatus(FAILED.name());
                                    return paymentLedgerService.recordEventPayment(expiredPayment)
                                            .then(publishExpiredPayment(expiredPayment));
                                })
                )
                .then();

    }

    private Mono<Void> publishExpiredPayment(Payment expiredPayment) {
        PaymentEventPayload payload = PaymentEventPayload.builder()
                .paymentId(expiredPayment.getId().toString())
                .transactionId(expiredPayment.getTransactionId())
                .correlationId(expiredPayment.getCorrelationId())
                .failureCode(ErrorCode.PAYMENT_EXPIRED.getCode())
                .failureMessage(ErrorCode.PAYMENT_EXPIRED.getDefaultMessage())
                .build();

        return paymentEventProducer.send(AppConstant.TOPICS.PAYMENT_FAILED,
                UUID.randomUUID().toString(), payload);
    }
}
