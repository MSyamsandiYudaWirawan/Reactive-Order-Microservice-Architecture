package com.MSyamsandiYW.payment_service.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
@Component
public class AppProperties {

    private String orderServiceUrl = "http://localhost:8081";
    private String getStatusOrder = "/api/v1/orders/status/{transactionId}";
    private Map<String, String> paymentMethodUrlMap = new HashMap<>(
            Map.of(
                    "DEBIT_CARD", "http://debitcard.com/payment/xxx",
                    "CREDIT_CARD", "http://creditcard.com/payment/xxx",
                    "BCA_VA", "http://bca.com/payment/xxx",
                    "BNI_VA", "http://bni.com/payment/xxx"
            ));
}
