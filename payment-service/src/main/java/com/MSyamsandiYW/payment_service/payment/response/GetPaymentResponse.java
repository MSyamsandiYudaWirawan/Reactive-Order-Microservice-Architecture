package com.MSyamsandiYW.payment_service.payment.response;

import lombok.*;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetPaymentResponse {
    private String transactionId;
    private String paymentMethod;
    private Double amount;
    private String status;
    private Instant createdDate;
}
