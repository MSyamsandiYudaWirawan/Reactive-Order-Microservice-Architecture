package com.MSyamsandiYW.order_service.client;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.exception.ErrorResponse;
import com.MSyamsandiYW.order_service.client.request.GetProductsRequest;
import com.MSyamsandiYW.order_service.client.response.GetProductResponse;
import com.MSyamsandiYW.order_service.properties.AppProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceClient {
    private final WebClient webClient;
    private final AppProperties appProperties;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public Mono<List<GetProductResponse>> getProductsById(String token, GetProductsRequest request) {

        String url = UriComponentsBuilder.fromUriString(appProperties.getInventoryServiceUrl())
                .path(appProperties.getGetProductsById())
                .build()
                .toUriString();

        return webClient.post()
                .uri(url)
                .header("Authorization", token)
                .header("Content-Type", MediaType.APPLICATION_JSON.toString())
                .header("Accept",MediaType.APPLICATION_JSON.toString())
                .bodyValue(request)
                .retrieve()
                // intercept error and mapping ErrorCode then throw exception with ErrorCode
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(ErrorResponse.class)
                                .flatMap(error -> Mono.error(new BusinessException(
                                        ErrorCode.fromCode(error.getCode())))))
                .bodyToFlux(GetProductResponse.class)
                .collectList()
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(e -> !(e instanceof BusinessException), e -> {
                    log.error("Failed to connect to inventory-service: {}", e.getMessage());
                    return new BusinessException(ErrorCode.INVENTORY_SERVICE_UNAVAILABLE);
                });
    }
}
