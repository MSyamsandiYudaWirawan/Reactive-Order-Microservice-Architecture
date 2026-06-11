package com.MSyamsandiYW.payment_service.payment.response;

import lombok.*;

import java.time.ZonedDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetPaymentByUser {
    private String transactionId;
    private String paymentMethod;
    private Double amount;
    private String status;
    private ZonedDateTime createdDate;
}
