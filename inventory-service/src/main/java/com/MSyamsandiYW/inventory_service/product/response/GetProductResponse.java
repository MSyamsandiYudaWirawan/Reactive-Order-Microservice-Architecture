package com.MSyamsandiYW.inventory_service.product.response;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetProductResponse {
    private String productId;
    private BigDecimal price;
}
