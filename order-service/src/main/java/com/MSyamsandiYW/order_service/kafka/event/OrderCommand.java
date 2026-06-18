package com.MSyamsandiYW.order_service.kafka.event;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderCommand {
    private String correlationId;
    private String transactionId;
    private String failureCode;
    private String failureMessage;
}
