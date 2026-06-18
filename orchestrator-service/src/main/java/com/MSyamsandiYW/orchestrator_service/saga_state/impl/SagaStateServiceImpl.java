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
                .createdDate(Instant.now())
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
        sagaState.setLastModifiedDate(Instant.now());
        return sagaStateRepository.save(sagaState);
    }

    @Override
    public Flux<SagaState> saveAll(List<SagaState> sagaStateList) {
        return sagaStateRepository.saveAll(sagaStateList.stream().peek(sagaState -> {
            sagaState.setUpdatedBy("ORCHESTRATION_SERVICE");
            sagaState.setLastModifiedDate(Instant.now());
        }).toList());
    }

    @Override
    public Flux<SagaState> findAllExpiredTransaction(Instant cutoff) {
        return sagaStateRepository.findAllExpiredTransaction(cutoff);
    }
}
