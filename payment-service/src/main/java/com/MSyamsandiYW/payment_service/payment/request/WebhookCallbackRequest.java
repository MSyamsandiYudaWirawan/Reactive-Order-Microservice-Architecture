package com.MSyamsandiYW.payment_service.payment.request;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class WebhookCallbackRequest {

    private String transactionId;
    private String paymentStatus;
    private String failureCode;
    private String failureMessage;
}
