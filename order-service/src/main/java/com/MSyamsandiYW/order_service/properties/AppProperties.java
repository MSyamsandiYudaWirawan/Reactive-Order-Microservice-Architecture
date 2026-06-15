package com.MSyamsandiYW.order_service.properties;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
@Component
public class AppProperties {

    private String inventoryServiceUrl = "http://localhost:8083";
    private String getProductsById = "/api/v1/products/list";
}
