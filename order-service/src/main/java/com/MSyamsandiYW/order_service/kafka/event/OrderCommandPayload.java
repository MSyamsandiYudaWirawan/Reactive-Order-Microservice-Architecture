package com.MSyamsandiYW.order_service.kafka.event;

import com.MSyamsandiYW.order_service.order_item.request.OrderItemRequest;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderCommandPayload {
    private String orderId;
    private String transactionId;
    private String correlationId;
    private List<OrderItemRequest> items;
}
