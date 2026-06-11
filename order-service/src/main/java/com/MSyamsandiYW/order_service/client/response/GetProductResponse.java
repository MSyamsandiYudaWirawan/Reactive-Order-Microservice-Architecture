package com.MSyamsandiYW.order_service.client.response;

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
