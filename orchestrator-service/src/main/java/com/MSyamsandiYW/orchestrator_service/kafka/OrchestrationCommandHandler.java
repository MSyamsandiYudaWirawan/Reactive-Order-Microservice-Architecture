package com.MSyamsandiYW.orchestrator_service.kafka;

import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorEventPayload;
import com.MSyamsandiYW.orchestrator_service.properties.AppConstant;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaState;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.UUID;

import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.PAYMENT_STATUS.INITIATED;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.PAYMENT_STATUS.PAID;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.SAGA_STATUS.COMPENSATING;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.SAGA_STATUS.COMPLETED;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.STOCK_STATUS.OUT_OF_STOCK;
import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.STOCK_STATUS.RESERVED;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrchestrationCommandHandler {

    private final SagaStateService sagaStateService;
    private final OrchestratorEventProducer producer;

    public Mono<Void> handleStockReserveCompleted(OrchestratorCommand payload) {
        // find saga by transaction id
        return sagaStateService.findByTransactionId(payload.getTransactionId())
                // create saga if there is no saga state
                .switchIfEmpty(sagaStateService.create(payload.getTransactionId(), payload.getCorrelationId()))
                // set stock status to reserved
                .flatMap(sagaState -> {
                    // if payment status is paid then handle saga completed
                    if (sagaState.getPaymentStatus().equalsIgnoreCase(PAID.name())) {
                        return handleSagaCompensated(sagaState);
                    }
                    sagaState.setStockStatus(RESERVED.name());
                    return sagaStateService.save(sagaState);
                })
                .then();
    }

    public Mono<Void> handlePaymentInitiated(OrchestratorCommand payload) {
        // find saga by transaction id
        return sagaStateService.findByTransactionId(payload.getTransactionId())
                // create saga if there is no saga state
                .switchIfEmpty(sagaStateService.create(payload.getTransactionId(), payload.getCorrelationId()))
                // set payment status to initiated
                .flatMap(sagaState -> {
                    sagaState.setPaymentStatus(INITIATED.name());
                    sagaState.setUpdatedBy("ORCHESTRATION_SERVICE");
                    sagaState.setLastModifiedDate(ZonedDateTime.now());
                    return sagaStateService.save(sagaState);
                })
                .then();

    }

    public Mono<Void> handlePaymentCompleted(OrchestratorCommand payload) {
        // find saga state by transactionId
        return sagaStateService.findByTransactionId(payload.getTransactionId())
                // create saga if there is no saga state
                .switchIfEmpty(sagaStateService.create(payload.getTransactionId(), payload.getCorrelationId()))
                .flatMap(sagaState -> {
                    // if payment status is paid then handle saga completed
                    if (sagaState.getStockStatus() != null &&
                            sagaState.getStockStatus().equalsIgnoreCase(RESERVED.name())) {
                        return handleSagaCompleted(sagaState);
                    }
                    // if stock status is out of stock then handle saga compensate
                    if (sagaState.getSagaStatus() != null &&
                            sagaState.getSagaStatus().equalsIgnoreCase(OUT_OF_STOCK.name())) {
                        return handleSagaCompensated(sagaState);
                    }

                    // set payment status to paid
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
        //find saga state by transactionId
        return sagaStateService.findByTransactionId(payload.getTransactionId())
                // create saga if there is no saga state
                .switchIfEmpty(sagaStateService.create(payload.getTransactionId(), payload.getCorrelationId()))
                .flatMap(sagaState -> {
                    if (sagaState.getPaymentStatus() != null &&
                            sagaState.getPaymentStatus().equalsIgnoreCase(PAID.name())) {
                        return handleSagaCompensated(sagaState);
                    }
                    sagaState.setStockStatus(OUT_OF_STOCK.name());
                    return sagaStateService.save(sagaState);
                })
                .then();
    }

    private Mono<SagaState> handleSagaCompleted(SagaState sagaState) {
        // set saga status to completed and send event to order completed topic
        sagaState.setSagaStatus(COMPLETED.name());
        sagaState.setUpdatedBy("ORCHESTRATION_SERVICE");
        sagaState.setLastModifiedDate(ZonedDateTime.now());

        //create payload for event
        OrchestratorEventPayload producePayload = OrchestratorEventPayload.builder()
                .transactionId(sagaState.getTransactionId())
                .correlationId(sagaState.getCorrelationId())
                .build();

        // save saga state
        return sagaStateService.save(sagaState)
                //send event order complete
                .flatMap(updatedSaga ->
                        producer.send(AppConstant.TOPICS.ORDER_COMPLETED, UUID.randomUUID().toString(), producePayload)
                                .thenReturn(updatedSaga))
                //send event deduct stock and return updated saga
                .flatMap(updatedSaga ->
                        producer.send(AppConstant.TOPICS.DEDUCT_STOCK, UUID.randomUUID().toString(), producePayload)
                                .thenReturn(updatedSaga));
    }

    private Mono<SagaState> handleSagaCompensated(SagaState sagaState) {
        // set saga status to completed and send event to order completed topic
        sagaState.setSagaStatus(COMPENSATING.name());
        sagaState.setUpdatedBy("ORCHESTRATION_SERVICE");
        sagaState.setLastModifiedDate(ZonedDateTime.now());

        //create payload for event
        OrchestratorEventPayload producePayload = OrchestratorEventPayload.builder()
                .transactionId(sagaState.getTransactionId())
                .correlationId(sagaState.getCorrelationId())
                .build();

        // save saga state
        return sagaStateService.save(sagaState)
                //send event refund requested and return updated saga
                .flatMap(updatedSaga ->
                        producer.send(AppConstant.TOPICS.REFUND_REQUESTED, UUID.randomUUID().toString(), producePayload)
                                .thenReturn(updatedSaga));
    }
}
