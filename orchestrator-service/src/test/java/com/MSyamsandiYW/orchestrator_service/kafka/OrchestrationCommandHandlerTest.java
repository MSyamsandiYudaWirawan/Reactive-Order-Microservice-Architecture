package com.MSyamsandiYW.orchestrator_service.kafka;

import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
import com.MSyamsandiYW.orchestrator_service.properties.AppConstant;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaState;
import com.MSyamsandiYW.orchestrator_service.saga_state.SagaStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestrationCommandHandlerTest {

    @Mock
    private SagaStateService sagaStateService;

    @Mock
    private OrchestratorEventProducer producer;

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
                .createdDate(Instant.now())
                .build();
    }

    @Test
    @DisplayName("handleStockReserveCompleted - no payment yet, should set stock RESERVED and wait")
    void handleStockReserveCompleted_noPayment_shouldWait() {
        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleStockReserveCompleted(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
        verify(producer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("handleStockReserveCompleted - payment already PAID, should complete saga")
    void handleStockReserveCompleted_paymentPaid_shouldCompleteSaga() {
        sagaState.setPaymentStatus(AppConstant.PAYMENT_STATUS.PAID.name());
        sagaState.setPaymentId(command.getPaymentId());

        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any()))
                .thenReturn(Mono.just(1));
        when(producer.send(any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockReserveCompleted(command))
                .verifyComplete();

        verify(producer).send(eq(AppConstant.TOPICS.ORDER_COMPLETED), any(), any());
        verify(producer).send(eq(AppConstant.TOPICS.DEDUCT_STOCK), any(), any());
    }

    @Test
    @DisplayName("handlePaymentCompleted - stock already RESERVED, should complete saga")
    void handlePaymentCompleted_stockReserved_shouldCompleteSaga() {
        sagaState.setStockStatus(AppConstant.STOCK_STATUS.RESERVED.name());

        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any()))
                .thenReturn(Mono.just(1));
        when(producer.send(any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(producer).send(eq(AppConstant.TOPICS.ORDER_COMPLETED), any(), any());
        verify(producer).send(eq(AppConstant.TOPICS.DEDUCT_STOCK), any(), any());
    }

    @Test
    @DisplayName("handlePaymentCompleted - stock OUT_OF_STOCK, should trigger compensation (refund)")
    void handlePaymentCompleted_outOfStock_shouldCompensate() {
        sagaState.setStockStatus(AppConstant.STOCK_STATUS.OUT_OF_STOCK.name());

        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any()))
                .thenReturn(Mono.just(1));
        when(producer.send(any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(producer).send(eq(AppConstant.TOPICS.REFUND_REQUESTED), any(), any());
    }

    @Test
    @DisplayName("handlePaymentCompleted - no stock result yet, should set PAID and wait")
    void handlePaymentCompleted_noStockResult_shouldWait() {
        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
        verify(producer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("handleOutOfStock - payment already PAID, should trigger compensation")
    void handleOutOfStock_paymentPaid_shouldCompensate() {
        sagaState.setPaymentStatus(AppConstant.PAYMENT_STATUS.PAID.name());
        sagaState.setPaymentId(command.getPaymentId());

        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.updateStatusIfInProgress(any(), any(), any()))
                .thenReturn(Mono.just(1));
        when(producer.send(any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOutOfStock(command))
                .verifyComplete();

        verify(producer).send(eq(AppConstant.TOPICS.REFUND_REQUESTED), any(), any());
    }

    @Test
    @DisplayName("handleOutOfStock - no payment, should mark saga FAILED")
    void handleOutOfStock_noPayment_shouldMarkFailed() {
        when(sagaStateService.findByTransactionId(command.getTransactionId()))
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
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("handlePaymentInitiated - should set payment status to INITIATED")
    void handlePaymentInitiated_shouldSetInitiated() {
        when(sagaStateService.findByTransactionId(command.getTransactionId()))
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
        sagaState.setSagaStatus(AppConstant.SAGA_STATUS.COMPENSATING.name());

        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleOrderRefundCompleted(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
    }

    @Test
    @DisplayName("handleOrderRefundFailed - should mark saga FAILED")
    void handleOrderRefundFailed_shouldMarkFailed() {
        sagaState.setSagaStatus(AppConstant.SAGA_STATUS.COMPENSATING.name());

        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleOrderRefundFailed(command))
                .verifyComplete();

        verify(sagaStateService).save(any(SagaState.class));
    }

    @Test
    @DisplayName("handleStockReserveCompleted - new transaction, should create saga")
    void handleStockReserveCompleted_newTransaction_shouldCreateSaga() {
        when(sagaStateService.findByTransactionId(command.getTransactionId()))
                .thenReturn(Mono.empty());
        when(sagaStateService.create(command.getTransactionId(), command.getCorrelationId()))
                .thenReturn(Mono.just(sagaState));
        when(sagaStateService.save(any(SagaState.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(handler.handleStockReserveCompleted(command))
                .verifyComplete();

        verify(sagaStateService).create(command.getTransactionId(), command.getCorrelationId());
    }
}
