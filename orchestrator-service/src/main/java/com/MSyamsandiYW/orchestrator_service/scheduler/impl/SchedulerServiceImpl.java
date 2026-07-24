package com.MSyamsandiYW.orchestrator_service.scheduler.impl;

import com.MSyamsandiYW.common.exception.ErrorCode;
import io.r2dbc.postgresql.codec.Json;
import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorEventPayload;
import com.MSyamsandiYW.orchestrator_service.outbox.Outbox;
import com.MSyamsandiYW.orchestrator_service.outbox.OutboxService;
import com.MSyamsandiYW.orchestrator_service.properties.AppConstant;
import com.MSyamsandiYW.orchestrator_service.properties.AppProperties;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaState;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateService;
import com.MSyamsandiYW.orchestrator_service.scheduler.SchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.PAYMENT_STATUS.INITIATED;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.PAYMENT_STATUS.PAID;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.STOCK_STATUS.RESERVED;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {
    private final AppProperties appProperties;
    private final SagaStateService sagaStateService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Void> executeScheduler() {
        Instant cutoff = Instant.now().minusSeconds(appProperties.getOrderExpirySeconds());
        log.info("Finding expired transactions with cutoff: {}", cutoff);
        return sagaStateService.findAllExpiredTransaction(cutoff).collectList()
                .flatMap(expiredList -> {
                    log.info("Found {} expired transactions", expiredList.size());

                    // stock reserved but payment not completed — release stock
                    List<SagaState> expiredReservedStock = expiredList.stream().filter(sagaState ->
                            RESERVED.name().equalsIgnoreCase(sagaState.getStockStatus()) &&
                                    !PAID.name().equalsIgnoreCase(sagaState.getPaymentStatus())
                    ).toList();

                    // payment completed but stock not reserved — refund payment
                    List<SagaState> expiredPaidTransaction = expiredList.stream().filter(sagaState ->
                            !RESERVED.name().equalsIgnoreCase(sagaState.getStockStatus()) &&
                                    PAID.name().equalsIgnoreCase(sagaState.getPaymentStatus())
                    ).toList();

                    // neither stock reserved nor payment completed — just fail
                    List<SagaState> expiredFailTransaction = expiredList.stream().filter(sagaState ->
                            !RESERVED.name().equalsIgnoreCase(sagaState.getStockStatus()) &&
                                    !INITIATED.name().equalsIgnoreCase(sagaState.getPaymentStatus()) &&
                                    !PAID.name().equalsIgnoreCase(sagaState.getPaymentStatus())
                    ).toList();
                    return handleExpired(expiredReservedStock, expiredPaidTransaction, expiredFailTransaction);
                });
    }

    private Mono<Void> handleExpired(List<SagaState> expiredReservedStock, List<SagaState> expiredPaidTransaction, List<SagaState> expiredFailTransaction) {
        return handleExpiredReservedStock(expiredReservedStock)
                .then(handleExpiredPaidTransaction(expiredPaidTransaction))
                .then(handleExpiredFailTransaction(expiredFailTransaction));
    }

    private Mono<Void> handleExpiredReservedStock(List<SagaState> sagaStateList) {
        if (sagaStateList.isEmpty()) return Mono.empty();
        log.info("Handling {} expired reserved-stock transactions — releasing stock", sagaStateList.size());
        return Flux.fromIterable(sagaStateList)
                .flatMap(sagaState ->
                        //update using Conditional UPDATE CAS for atomic
                        sagaStateService.updateStatusIfInProgress(
                                        sagaState.getTransactionId(),
                                        AppConstant.SAGA_STATUS.FAILED.name(),
                                        sagaState.getPaymentStatus(),
                                        ErrorCode.ORDER_EXPIRED.getCode(),
                                        ErrorCode.ORDER_EXPIRED.getDefaultMessage()
                                )
                                .filter(rows -> rows > 0)
                                // insert outbox event ORDER_EXPIRED
                                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState, ErrorCode.ORDER_EXPIRED.getCode(), ErrorCode.ORDER_EXPIRED.getDefaultMessage()), AppConstant.TOPICS.ORDER_EXPIRED, "ORDER_EXPIRED").thenReturn(updatedSagaState))
                                // insert outbox event RELEASE_STOCK
                                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState, ErrorCode.ORDER_EXPIRED.getCode(), ErrorCode.ORDER_EXPIRED.getDefaultMessage()), AppConstant.TOPICS.RELEASE_STOCK, "RELEASE_STOCK"))
                                // flag as transactional
                                .as(transactionalOperator::transactional)
                                .then()
                )
                .then();
    }

    private Mono<Void> handleExpiredPaidTransaction(List<SagaState> sagaStateList) {
        if (sagaStateList.isEmpty()) return Mono.empty();
        log.info("Handling {} expired paid transactions — requesting refund", sagaStateList.size());
        return Flux.fromIterable(sagaStateList)
                .flatMap(sagaState ->
                        //update using Conditional UPDATE CAS for atomic
                        sagaStateService.updateStatusIfInProgress(
                                        sagaState.getTransactionId(),
                                        AppConstant.SAGA_STATUS.COMPENSATING.name(),
                                        sagaState.getPaymentStatus(),
                                        ErrorCode.ORDER_EXPIRED.getCode(),
                                        ErrorCode.ORDER_EXPIRED.getDefaultMessage()
                                )
                                .filter(rows -> rows > 0)
                                // insert outbox event ORDER_EXPIRED
                                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState, ErrorCode.ORDER_EXPIRED.getCode(), ErrorCode.ORDER_EXPIRED.getDefaultMessage()), AppConstant.TOPICS.ORDER_EXPIRED, "ORDER_EXPIRED").thenReturn(updatedSagaState))
                                // insert outbox event REFUND_REQUESTED
                                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState, ErrorCode.ORDER_EXPIRED.getCode(), ErrorCode.ORDER_EXPIRED.getDefaultMessage()), AppConstant.TOPICS.REFUND_REQUESTED, "REFUND_REQUESTED"))
                                // flag as transactional
                                .as(transactionalOperator::transactional)
                                .then()
                )
                .then();
    }

    private Mono<Void> handleExpiredFailTransaction(List<SagaState> sagaStateList) {
        if (sagaStateList.isEmpty()) return Mono.empty();
        log.info("Handling {} expired failed transactions — marking as failed", sagaStateList.size());
        return Flux.fromIterable(sagaStateList)
                .flatMap(sagaState ->
                        //update using Conditional UPDATE CAS for atomic
                        sagaStateService.updateStatusIfInProgress(
                                        sagaState.getTransactionId(),
                                        AppConstant.SAGA_STATUS.FAILED.name(),
                                        sagaState.getPaymentStatus(),
                                        ErrorCode.ORDER_EXPIRED.getCode(),
                                        ErrorCode.ORDER_EXPIRED.getDefaultMessage()
                                )
                                .filter(rows -> rows > 0)
                                // insert outbox event ORDER_EXPIRED
                                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState, ErrorCode.ORDER_EXPIRED.getCode(), ErrorCode.ORDER_EXPIRED.getDefaultMessage()), AppConstant.TOPICS.ORDER_EXPIRED, "ORDER_EXPIRED").thenReturn(updatedSagaState))
                                // flag as transactional
                                .as(transactionalOperator::transactional)
                                .then()
                )
                .then();
    }

    private OrchestratorEventPayload buildEventPayload(SagaState sagaState, String failureCode, String failureMessage) {
        return OrchestratorEventPayload.builder()
                .paymentId(sagaState.getPaymentId())
                .correlationId(sagaState.getCorrelationId())
                .transactionId(sagaState.getTransactionId())
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    private Mono<Void> insertOutbox(OrchestratorEventPayload payload, String topic, String eventName) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> Outbox.builder()
                        .aggregateId(UUID.randomUUID().toString())
                        .aggregateType(topic)
                        .eventType(eventName)
                        .payload(Json.of(json))
                        .createdAt(Instant.now())
                        .build())
                .flatMap(outboxService::save)
                .then();
    }
}
