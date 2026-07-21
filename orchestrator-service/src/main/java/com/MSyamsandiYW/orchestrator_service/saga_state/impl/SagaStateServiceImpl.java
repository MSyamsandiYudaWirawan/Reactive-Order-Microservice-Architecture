package com.MSyamsandiYW.orchestrator_service.saga_state.impl;

import com.MSyamsandiYW.orchestrator_service.saga_state.SagaState;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateRepository;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.SAGA_STATUS.IN_PROGRESS;

@Service
@Slf4j
@RequiredArgsConstructor
public class SagaStateServiceImpl implements SagaStateService {
    private final SagaStateRepository sagaStateRepository;

    @Override
    public Mono<SagaState> findByTransactionId(String transactionId) {
        return sagaStateRepository.findFirstByTransactionId(transactionId);
    }

    @Override
    public Mono<SagaState> create(String transactionId, String correlationId) {
        SagaState sagaState = SagaState.builder()
                .transactionId(transactionId)
                .correlationId(correlationId)
                .sagaStatus(IN_PROGRESS.name())
                .createdBy("ORCHESTRATION_SERVICE")
                .createdAt(Instant.now())
                .build();
        return sagaStateRepository.save(sagaState);
    }

    @Override
    public Mono<Integer> updateStatusIfInProgress(String transactionId, String newSagaStatus, String newPaymentStatus) {
        return sagaStateRepository.updateStatusIfInProgress(transactionId, newSagaStatus, newPaymentStatus);
    }

    @Override
    public Mono<SagaState> save(SagaState sagaState) {
        sagaState.setUpdatedBy("ORCHESTRATION_SERVICE");
        sagaState.setUpdatedAt(Instant.now());
        return sagaStateRepository.save(sagaState);
    }

    @Override
    public Flux<SagaState> findAllExpiredTransaction(Instant cutoff) {
        return sagaStateRepository.findAllExpiredTransaction(cutoff);
    }

    @Override
    public Mono<SagaState> findOrCreate(String transactionId, String correlationId) {
        return sagaStateRepository.insertIfAbsent(transactionId,correlationId)
                .then(sagaStateRepository.findFirstByTransactionId(transactionId));
    }

    @Override
    public Mono<Void> updateCompensatingStatus(String transactionId, String name) {
        return sagaStateRepository.updateCompensatingStatus(transactionId, name)
                .filter(rows -> rows > 0)
                .map(__ -> {
                    log.info("Compensation status updated for transactionId: {}", transactionId);
                    return Mono.empty();
                })
                .then();
    }
}
