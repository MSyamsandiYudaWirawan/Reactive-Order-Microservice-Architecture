package com.MSyamsandiYW.inventory_service.kafka.event;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class StockItem {

    @NotNull
    private String productId;
    @NotNull
    private Integer quantity;
}
