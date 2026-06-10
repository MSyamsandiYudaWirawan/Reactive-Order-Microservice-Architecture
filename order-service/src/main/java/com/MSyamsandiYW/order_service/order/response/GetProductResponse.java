package com.MSyamsandiYW.order_service.order.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetProductResponse {
    private String productId;
    private Double price;
}
