package com.MSyamsandiYW.payment_service.payment.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CallbackPaymentMethodResponse {

    private String transactionId;
    private String paymentStatus;
    private String failureCode;
    private String failureMessage;
}
