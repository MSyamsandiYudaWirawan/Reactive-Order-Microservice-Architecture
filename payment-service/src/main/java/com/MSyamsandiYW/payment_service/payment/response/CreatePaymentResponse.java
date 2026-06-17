package com.MSyamsandiYW.payment_service.payment.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CreatePaymentResponse {
    private String transactionId;
    private Double amount;
    private String paymentMethod;
    private String urlPayment;
}
