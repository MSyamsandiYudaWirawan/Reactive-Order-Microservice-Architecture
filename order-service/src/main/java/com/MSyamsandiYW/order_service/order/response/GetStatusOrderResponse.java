package com.MSyamsandiYW.order_service.order.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetStatusOrderResponse {
    private String transactionId;
    private String correlationId;
    private String orderStatus;
    private String discountCode;
    private BigDecimal totalAmount;
    private Instant createdDate;
}
