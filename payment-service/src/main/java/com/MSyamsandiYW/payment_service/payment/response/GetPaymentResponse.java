package com.MSyamsandiYW.payment_service.payment.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetPaymentResponse {
    private String transactionId;
    private String paymentMethod;
    private BigDecimal amount;
    private String status;
    private Instant createdAt;
}
