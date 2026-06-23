package com.MSyamsandiYW.orchestrator_service.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
@Component
public class AppProperties {

    //TODO 2 minute for testing
    private Integer orderExpirySeconds = 60;
}
