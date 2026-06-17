package com.MSyamsandiYW.order_service.order_item.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderItemRequest {

    @NotNull
    private String productId;
    @NotNull
    private Integer quantity;
}
