package com.MSyamsandiYW.orchestrator_service.kafka;

import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
import com.MSyamsandiYW.orchestrator_service.outbox.Outbox;
import com.MSyamsandiYW.orchestrator_service.outbox.OutboxService;
import com.MSyamsandiYW.orchestrator_service.properties.AppConstant;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaState;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestrationCommandHandlerTest {

    @Mock
    private SagaStateService sagaStateService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrchestrationCommandHandler handler;

    private OrchestratorCommand command;
    private SagaState sagaState;

    @BeforeEach
    void setUp() {
        command = OrchestratorCommand.builder()
                .transactionId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .paymentId(UUID.randomUUID().toString())
                .build();

        sagaState = SagaState.builder()
                .id(UUID.randomUUID())
                .transactionId(command.getTransactionId())
                .correlationId(command.getCorrelationId())
                .sagaStatus(AppConstant.SAGA_STATUS.IN_PROGRESS.name())
                .createdBy("ORCHESTRATION_SERVICE")
                .createdAt(Instant.now())
                .build();

        // Default mock for findOrCreate — used by most handler methods
        lenient().when(sagaStateService.findOrCreate(any(OrchestratorCommand.class)))
                .thenReturn(Mono.just(sagaState));
        // Pass-through transaction boundary — unit test has no real transaction
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Default mock for outbox save
        lenient().when(outboxService.save(any(Outbox.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("handleStockReserveCompleted - no payment yet, should set stock RESERVED and wait")
    void handleStockReserveCompleted_noPayment_shouldWait() {
        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleStockReserveCompleted(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
        verify(outboxService, never()).save(any());
    }

    @Test
    @DisplayName("handleStockReserveCompleted - payment already PAID, should complete saga")
    void handleStockReserveCompleted_paymentPaid_shouldCompleteSaga() {
        sagaState.setPaymentStatus(AppConstant.PAYMENT_STATUS.PAID.name());
        sagaState.setPaymentId(command.getPaymentId());

        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(handler.handleStockReserveCompleted(command))
                .verifyComplete();

        verify(outboxService).save(argThat(o -> AppConstant.TOPICS.ORDER_COMPLETED.equals(o.getAggregateType())));
        verify(outboxService).save(argThat(o -> AppConstant.TOPICS.DEDUCT_STOCK.equals(o.getAggregateType())));
    }

    @Test
    @DisplayName("handlePaymentCompleted - stock already RESERVED, should complete saga")
    void handlePaymentCompleted_stockReserved_shouldCompleteSaga() {
        sagaState.setStockStatus(AppConstant.STOCK_STATUS.RESERVED.name());

        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(outboxService).save(argThat(o -> AppConstant.TOPICS.ORDER_COMPLETED.equals(o.getAggregateType())));
        verify(outboxService).save(argThat(o -> AppConstant.TOPICS.DEDUCT_STOCK.equals(o.getAggregateType())));
    }

    @Test
    @DisplayName("handlePaymentCompleted - stock OUT_OF_STOCK, should trigger compensation (refund)")
    void handlePaymentCompleted_outOfStock_shouldCompensate() {
        sagaState.setStockStatus(AppConstant.STOCK_STATUS.OUT_OF_STOCK.name());

        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(outboxService).save(argThat(o -> AppConstant.TOPICS.REFUND_REQUESTED.equals(o.getAggregateType())));
    }

    @Test
    @DisplayName("handlePaymentCompleted - no stock result yet, should set PAID and wait")
    void handlePaymentCompleted_noStockResult_shouldWait() {
        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
        verify(outboxService, never()).save(any());
    }

    @Test
    @DisplayName("handleOutOfStock - payment already PAID, should trigger compensation")
    void handleOutOfStock_paymentPaid_shouldCompensate() {
        sagaState.setPaymentStatus(AppConstant.PAYMENT_STATUS.PAID.name());
        sagaState.setPaymentId(command.getPaymentId());
        command.setFailureCode("OUT_OF_STOCK");
        command.setFailureMessage("Insufficient stock to fulfill the order");

        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(handler.handleOutOfStock(command))
                .verifyComplete();

        verify(outboxService).save(argThat(o -> AppConstant.TOPICS.REFUND_REQUESTED.equals(o.getAggregateType())
                && o.getPayload().contains("\"failureCode\":\"OUT_OF_STOCK\"")));
    }

    @Test
    @DisplayName("handleOutOfStock - no payment, should mark saga FAILED")
    void handleOutOfStock_noPayment_shouldMarkFailed() {
        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleOutOfStock(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
    }

    @Test
    @DisplayName("handlePaymentFailed - should just log and complete (user can retry)")
    void handlePaymentFailed_shouldComplete() {
        StepVerifier.create(handler.handlePaymentFailed(command))
                .verifyComplete();

        verifyNoInteractions(sagaStateService);
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("handlePaymentInitiated - should set payment status to INITIATED")
    void handlePaymentInitiated_shouldSetInitiated() {
        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handlePaymentInitiated(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
    }

    @Test
    @DisplayName("handleOrderRefundCompleted - should mark saga COMPLETED")
    void handleOrderRefundCompleted_shouldMarkCompleted() {
        when(sagaStateService.updateCompensatingStatus(command.getTransactionId(), AppConstant.SAGA_STATUS.COMPLETED.name()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderRefundCompleted(command))
                .verifyComplete();

        verify(sagaStateService).updateCompensatingStatus(command.getTransactionId(), AppConstant.SAGA_STATUS.COMPLETED.name());
    }

    @Test
    @DisplayName("handleOrderRefundFailed - should mark saga FAILED")
    void handleOrderRefundFailed_shouldMarkFailed() {
        when(sagaStateService.updateCompensatingStatus(command.getTransactionId(), AppConstant.SAGA_STATUS.FAILED.name()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderRefundFailed(command))
                .verifyComplete();

        verify(sagaStateService).updateCompensatingStatus(command.getTransactionId(), AppConstant.SAGA_STATUS.FAILED.name());
    }

    @Test
    @DisplayName("handleStockReserveRequested - should initialize saga via findOrCreate")
    void handleStockReserveRequested_shouldInitializeSaga() {
        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));

        StepVerifier.create(handler.handleStockReserveRequested(command))
                .verifyComplete();

        verify(sagaStateService).findOrCreate(command);
        verify(sagaStateService, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("handleStockReserveCompleted - new transaction, should create saga via findOrCreate")
    void handleStockReserveCompleted_newTransaction_shouldCreateSaga() {
        when(sagaStateService.findOrCreate(command))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleStockReserveCompleted(command))
                .verifyComplete();

        verify(sagaStateService).findOrCreate(command);
    }
}
