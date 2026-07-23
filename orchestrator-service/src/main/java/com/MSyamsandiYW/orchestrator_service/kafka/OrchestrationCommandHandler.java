package com.MSyamsandiYW.orchestrator_service.kafka;

import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorEventPayload;
import com.MSyamsandiYW.orchestrator_service.outbox.Outbox;
import com.MSyamsandiYW.orchestrator_service.outbox.OutboxService;
import com.MSyamsandiYW.orchestrator_service.properties.AppConstant;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaState;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.PAYMENT_STATUS.INITIATED;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.PAYMENT_STATUS.PAID;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.SAGA_STATUS.*;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.STOCK_STATUS.RESERVED;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrchestrationCommandHandler {

    private final SagaStateService sagaStateService;
    private final TransactionalOperator transactionalOperator;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public Mono<Void> handleStockReserveCompleted(OrchestratorCommand payload) {
        // find or create saga by transaction id (atomic)
        return sagaStateService.findOrCreate(payload.getTransactionId(), payload.getCorrelationId())
                // set stock status to reserved
                .flatMap(sagaState -> {
                    // if payment status is paid then handle saga completed
                    if (PAID.name().equalsIgnoreCase(sagaState.getPaymentStatus())) {
                        log.info("Stock reserved + payment already PAID — completing saga - transactionId: {}", payload.getTransactionId());
                        return handleSagaCompleted(sagaState);
                    }
                    log.info("Stock reserved — waiting for payment - transactionId: {}", payload.getTransactionId());
                    sagaState.setStockStatus(RESERVED.name());
                    return sagaStateService.save(sagaState);
                })
                .then();
    }

    public Mono<Void> handlePaymentInitiated(OrchestratorCommand payload) {
        // find or create saga by transaction id (atomic)
        return sagaStateService.findOrCreate(payload.getTransactionId(), payload.getCorrelationId())
                // set payment status to initiated
                .flatMap(sagaState -> {
                    log.info("Payment initiated — tracking paymentId: {}, transactionId: {}", payload.getPaymentId(), payload.getTransactionId());
                    sagaState.setPaymentId(payload.getPaymentId());
                    sagaState.setPaymentStatus(INITIATED.name());
                    sagaState.setUpdatedBy("ORCHESTRATION_SERVICE");
                    sagaState.setUpdatedAt(Instant.now());
                    return sagaStateService.save(sagaState);
                })
                .then();
    }

    public Mono<Void> handlePaymentCompleted(OrchestratorCommand payload) {
        // find or create saga by transaction id (atomic)
        return sagaStateService.findOrCreate(payload.getTransactionId(), payload.getCorrelationId())
                .flatMap(sagaState -> {
                    // always set paymentId from the event
                    sagaState.setPaymentId(payload.getPaymentId());

                    // if stock is reserved then handle saga completed
                    if (RESERVED.name().equalsIgnoreCase(sagaState.getStockStatus())) {
                        log.info("Payment completed + stock already RESERVED — completing saga - transactionId: {}", payload.getTransactionId());
                        return handleSagaCompleted(sagaState);
                    }
                    // if stock status is out of stock then handle saga compensate
                    if (AppConstant.STOCK_STATUS.OUT_OF_STOCK.name().equalsIgnoreCase(sagaState.getStockStatus())) {
                        log.info("Payment completed + stock OUT_OF_STOCK — triggering compensation - transactionId: {}", payload.getTransactionId());
                        return handleSagaCompensated(sagaState);
                    }

                    // waiting for stock result
                    log.info("Payment completed — waiting for stock result - transactionId: {}", payload.getTransactionId());
                    sagaState.setPaymentStatus(PAID.name());
                    return sagaStateService.save(sagaState);
                })
                .then();
    }

    public Mono<Void> handlePaymentFailed(OrchestratorCommand payload) {
        return Mono.defer(() -> {
            log.info("payment failed for transactionId : {} with payload : {}, customer can retry payment", payload.getTransactionId(), payload);
            return Mono.empty();
        });
    }

    public Mono<Void> handleOutOfStock(OrchestratorCommand payload) {
        // find or create saga by transaction id (atomic)
        return sagaStateService.findOrCreate(payload.getTransactionId(), payload.getCorrelationId())
                .flatMap(sagaState -> {
                    // if payment already completed, trigger refund
                    if (PAID.name().equalsIgnoreCase(sagaState.getPaymentStatus())) {
                        log.info("Out of stock + payment already PAID — triggering compensation - transactionId: {}", payload.getTransactionId());
                        return handleSagaCompensated(sagaState);
                    }
                    //if payment is initiated (in progress), wait for payment result
                    if (INITIATED.name().equalsIgnoreCase(sagaState.getPaymentStatus())) {
                        log.info("Out of stock + payment INITIATED — waiting for payment result - transactionId: {}", payload.getTransactionId());
                        sagaState.setStockStatus(AppConstant.STOCK_STATUS.OUT_OF_STOCK.name());
                        return sagaStateService.save(sagaState);
                    }
                    // no payment at all, saga simply fails
                    log.info("Out of stock + no payment — saga failed - transactionId: {}", payload.getTransactionId());
                    sagaState.setStockStatus(AppConstant.STOCK_STATUS.OUT_OF_STOCK.name());
                    sagaState.setSagaStatus(FAILED.name());
                    return sagaStateService.save(sagaState);
                }).then();
    }

    private Mono<Void> handleSagaCompleted(SagaState sagaState) {
        //update saga status to completed and payment status to paid
        //update using Conditional UPDATE CAS for atomic
        return sagaStateService.updateStatusIfInProgress(
                        sagaState.getTransactionId(),
                        COMPLETED.name(),
                        PAID.name()
                )
                // if no rows updated return mono empty
                .filter(rowsUpdated -> rowsUpdated > 0)
                // insert outbox event ORDER_COMPLETED
                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState), AppConstant.TOPICS.ORDER_COMPLETED, "ORDER_COMPLETED").thenReturn(updatedSagaState))
                // insert outbox event DEDUCT_STOCK
                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState), AppConstant.TOPICS.DEDUCT_STOCK, "DEDUCT_STOCK"))
                // flag as transactional
                .as(transactionalOperator::transactional)
                .then();
    }


    private Mono<Void> handleSagaCompensated(SagaState sagaState) {

        //update saga status to compensating and payment status to paid
        //update using Conditional UPDATE CAS for atomic
        return sagaStateService.updateStatusIfInProgress(
                        sagaState.getTransactionId(),
                        COMPENSATING.name(),
                        // this already paid
                        PAID.name()
                )
                // if no rows updated return mono empty
                .filter(rowsUpdated -> rowsUpdated > 0)
                // insert outbox event REFUND_REQUESTED
                .flatMap(updatedSagaState -> insertOutbox(buildEventPayload(sagaState), AppConstant.TOPICS.REFUND_REQUESTED, "REFUND_REQUESTED"))
                .as(transactionalOperator::transactional)
                .then();
    }

    public Mono<Void> handleOrderRefundCompleted(OrchestratorCommand payload) {
        log.info("Refund completed — saga compensation done - transactionId: {}", payload.getTransactionId());
        return sagaStateService.updateCompensatingStatus(payload.getTransactionId(), COMPLETED.name())
                .then();
    }

    public Mono<Void> handleOrderRefundFailed(OrchestratorCommand payload) {
        log.error("Refund failed — manual intervention required - transactionId: {}", payload.getTransactionId());
        return sagaStateService.updateCompensatingStatus(payload.getTransactionId(), FAILED.name())
                .then();
    }

    public Mono<Void> handleStockReserveRequested(OrchestratorCommand payload) {
        log.info("Stock reserve requested - initializing saga - transactionId: {}", payload.getTransactionId());
        return sagaStateService.findOrCreate(payload.getTransactionId(), payload.getCorrelationId()).then();
    }


    private OrchestratorEventPayload buildEventPayload(SagaState sagaState) {
        return OrchestratorEventPayload.builder()
                .paymentId(sagaState.getPaymentId())
                .correlationId(sagaState.getCorrelationId())
                .transactionId(sagaState.getTransactionId())
                .build();
    }

    private Mono<Void> insertOutbox(OrchestratorEventPayload payload, String topic, String eventName) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> Outbox.builder()
                        .aggregateId(UUID.randomUUID().toString())
                        .aggregateType(topic)
                        .eventType(eventName)
                        .payload(json)
                        .createdAt(Instant.now())
                        .build())
                .flatMap(outboxService::save)
                .then();
    }
}
