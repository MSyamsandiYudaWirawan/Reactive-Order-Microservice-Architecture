package com.MSyamsandiYW.orchestrator_service.kafka.event;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrchestratorEventPayload {

    private String paymentId;
    private String correlationId;
    private String transactionId;
    private String failureCode;
    private String failureMessage;
}