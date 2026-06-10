package com.MSyamsandiYW.inventory_service.product.request;

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
