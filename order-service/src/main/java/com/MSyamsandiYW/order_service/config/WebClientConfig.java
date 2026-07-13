package com.MSyamsandiYW.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        // Custom DNS resolver for containerized environments (Docker/K8s):
        // - cacheNegativeTimeToLive: cache failed DNS lookups for only 5s (retry quickly when services come up)
        // - queryTimeout: fail fast if DNS server doesn't respond within 5s
        HttpClient httpClient = HttpClient.create()
                .resolver(spec -> spec
                        .cacheNegativeTimeToLive(Duration.ofSeconds(5))
                        .queryTimeout(Duration.ofSeconds(5))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
