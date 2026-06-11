package com.MSyamsandiYW.payment_service.payment.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CreatePaymentRequest {

    @NotBlank
    private String transactionId;

    @NotBlank
    private String paymentMethod;
}
