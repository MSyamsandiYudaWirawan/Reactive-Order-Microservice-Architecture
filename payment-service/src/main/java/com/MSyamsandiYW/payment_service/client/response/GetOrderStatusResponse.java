package com.MSyamsandiYW.payment_service.client.response;

import lombok.*;

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
    private Double totalAmount;
    private String paymentMethod;
    private ZonedDateTime createdDate;
}
