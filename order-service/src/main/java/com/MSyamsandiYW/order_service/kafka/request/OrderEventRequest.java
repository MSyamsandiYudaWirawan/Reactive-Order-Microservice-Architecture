package com.MSyamsandiYW.order_service.kafka.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderEventRequest {
    private String correlationId;
    private String transactionId;
    private String failureCode;
    private String failureMessage;
}
