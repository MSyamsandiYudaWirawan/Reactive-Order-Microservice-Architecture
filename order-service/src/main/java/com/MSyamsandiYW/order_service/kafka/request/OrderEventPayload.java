package com.MSyamsandiYW.order_service.kafka.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderEventPayload {
    private String orderId;
    private String correlationId;
    private String transactionId;
}
