package com.MSyamsandiYW.order_service.order.request;

import com.MSyamsandiYW.order_service.order_item.request.OrderItemRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CreateOrderRequest {

    @NotEmpty
    private List<OrderItemRequest> items;
    private String discountCode;
}
