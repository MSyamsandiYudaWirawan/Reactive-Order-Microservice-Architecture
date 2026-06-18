package com.MSyamsandiYW.payment_service.kafka.event;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PaymentEventPayload {

    private String paymentId;
    private String correlationId;
    private String transactionId;
    private String failureCode;
    private String failureMessage;
}
