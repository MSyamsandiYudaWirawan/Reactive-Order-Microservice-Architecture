package com.MSyamsandiYW.order_service.order.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CreateOrderResponse {
    private String transactionId;
}
