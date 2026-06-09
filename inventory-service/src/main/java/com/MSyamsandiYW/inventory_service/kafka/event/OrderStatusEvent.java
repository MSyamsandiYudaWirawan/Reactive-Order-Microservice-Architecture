package com.MSyamsandiYW.inventory_service.kafka.event;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderStatusEvent {
    private String correlationId;
    private String transactionId;
    private String failureCode;
    private String failureMessage;
}
