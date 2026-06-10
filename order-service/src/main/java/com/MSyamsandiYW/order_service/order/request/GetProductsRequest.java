package com.MSyamsandiYW.order_service.order.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetProductsRequest {
    @NotEmpty
    private List<String> productIds;
}
