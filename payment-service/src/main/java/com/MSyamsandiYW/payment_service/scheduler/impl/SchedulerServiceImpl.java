package com.MSyamsandiYW.payment_service.scheduler.impl;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentEventPayload;
import com.MSyamsandiYW.payment_service.outbox.Outbox;
import com.MSyamsandiYW.payment_service.outbox.OutboxService;
import com.MSyamsandiYW.payment_service.payment.Payment;
import com.MSyamsandiYW.payment_service.payment.PaymentRepository;
import com.MSyamsandiYW.payment_service.payment_ledger.PaymentLedgerService;
import com.MSyamsandiYW.payment_service.properties.AppProperties;
import com.MSyamsandiYW.payment_service.scheduler.SchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static com.MSyamsandiYW.common.exception.ErrorCode.PAYMENT_EXPIRED;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.PAYMENT_STATUS.FAILED;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.PAYMENT_FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerServiceImpl implements SchedulerService {

    private final AppProperties appProperties;
    private final PaymentRepository paymentRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final ObjectMapper objectMapper;
    private final OutboxService outboxService;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Void> executeScheduler() {
        Instant cutoff = Instant.now().minusSeconds(appProperties.getPaymentExpirySeconds());
        log.info("Finding expired payment with cutoff: {}", cutoff);

        return paymentRepository.findAllExpiredPayments(cutoff)
                .flatMap(expiredPayment ->
                        // CAS update: only marks FAILED if still PENDING (prevents race with webhook)
                        paymentRepository.updatePendingStatusPayment(expiredPayment.getId(), FAILED.name(), PAYMENT_EXPIRED.getCode(), PAYMENT_EXPIRED.getDefaultMessage())
                                .filter(rowsUpdated -> rowsUpdated > 0)
                                .flatMap(__ -> {
                                    // sync in-memory object with what the CAS just wrote (for ledger + outbox payload)
                                    expiredPayment.setStatus(FAILED.name());
                                    expiredPayment.setFailureCode(PAYMENT_EXPIRED.getCode());
                                    expiredPayment.setFailureMessage(PAYMENT_EXPIRED.getDefaultMessage());
                                    // save payment ledger and insert outbox
                                    return paymentLedgerService.recordEventPayment(expiredPayment)
                                            .then(insertOutbox(buildPayload(expiredPayment), PAYMENT_FAILED, "PAYMENT_FAILED"));
                                })
                                // CAS + ledger + outbox commit or roll back together
                                .as(transactionalOperator::transactional)
                                // TODO: AFTER commit, best-effort notify provider to cancel/expire the payment
                                // (HTTP call must NOT be inside the transaction — it can't roll back).
                                // Retry with backoff, then give up: if this fails or the user is mid-checkout,
                                // the late-webhook silent refund in applyWebhookGuards is the safety net.
                )
                .then();

    }


    private PaymentEventPayload buildPayload(Payment payment) {
        return PaymentEventPayload.builder()
                .paymentId(payment.getId().toString())
                .transactionId(payment.getTransactionId())
                .correlationId(payment.getCorrelationId())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .build();
    }

    private Mono<Void> insertOutbox(PaymentEventPayload payload, String topic, String eventName) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> Outbox.builder()
                        .aggregateId(UUID.randomUUID().toString())
                        .aggregateType(topic)
                        .eventType(eventName)
                        .payload(json)
                        .build())
                .flatMap(outboxService::save);
    }
}
