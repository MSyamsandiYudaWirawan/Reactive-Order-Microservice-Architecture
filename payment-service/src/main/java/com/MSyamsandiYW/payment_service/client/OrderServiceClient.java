package com.MSyamsandiYW.payment_service.client;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.exception.ErrorResponse;
import com.MSyamsandiYW.payment_service.client.response.GetOrderStatusResponse;
import com.MSyamsandiYW.payment_service.properties.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceClient {
    private final WebClient webClient;
    private final AppProperties appProperties;

    public Mono<GetOrderStatusResponse> getStatusOrder(String transactionId, String token) {
        String url = UriComponentsBuilder.fromUriString(appProperties.getOrderServiceUrl())
                .path(appProperties.getGetStatusOrder())
                .buildAndExpand(transactionId)
                .toUriString();

        return webClient.get()
                .uri(url)
                .header("Authorization", token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(ErrorResponse.class)
                        .flatMap(error ->
                                Mono.error(new BusinessException(ErrorCode.fromCode(error.getCode())))))
                .bodyToMono(GetOrderStatusResponse.class);
    }
}
