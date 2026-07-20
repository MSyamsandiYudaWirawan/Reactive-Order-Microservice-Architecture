package com.MSyamsandiYW.order_service.client.response;

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
