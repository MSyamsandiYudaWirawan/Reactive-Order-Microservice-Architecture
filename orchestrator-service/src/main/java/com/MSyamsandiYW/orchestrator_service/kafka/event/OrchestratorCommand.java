package com.MSyamsandiYW.orchestrator_service.kafka.event;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrchestratorCommand {

    private String paymentId;
    private String transactionId;
    private String correlationId;
}
