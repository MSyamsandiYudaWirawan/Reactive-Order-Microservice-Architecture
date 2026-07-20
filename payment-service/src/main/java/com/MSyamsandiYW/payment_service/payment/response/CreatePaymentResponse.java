package com.MSyamsandiYW.payment_service.payment.response;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CreatePaymentResponse {
    private String transactionId;
    private BigDecimal amount;
    private String paymentMethod;
    private String urlPayment;
    // TODO: Remove paymentId — testing only
    private String paymentId;
}
