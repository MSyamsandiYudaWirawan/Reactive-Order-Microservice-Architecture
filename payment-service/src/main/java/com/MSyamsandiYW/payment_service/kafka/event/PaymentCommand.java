package com.MSyamsandiYW.payment_service.kafka.event;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PaymentCommand {

    private String paymentId;
    private String transactionId;
    private String correlationId;
}
