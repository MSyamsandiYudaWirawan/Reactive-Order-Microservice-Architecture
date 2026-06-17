package com.MSyamsandiYW.inventory_service.kafka.event;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class StockCommand {
    private String orderId;
    private String transactionId;
    private String correlationId;
    private List<StockItem> items;
}
