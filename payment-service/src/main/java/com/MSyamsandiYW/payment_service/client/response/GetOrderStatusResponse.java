package com.MSyamsandiYW.payment_service.client.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetOrderStatusResponse {
    private String transactionId;
    private String correlationId;
    private String orderStatus;
    private String discountCode;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private ZonedDateTime createdAt;
}
