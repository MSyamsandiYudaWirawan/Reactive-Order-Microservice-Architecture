package com.MSyamsandiYW.gateway_service.handler;

import com.MSyamsandiYW.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;

@Component
@Order(-2)
@RequiredArgsConstructor
public class ApplicationExceptionHandler  implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if(ex instanceof BusinessException bex){
            exchange.getResponse().setStatusCode(bex.getErrorCode().getStatus());
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String body = """
                    {"code":"%s","message":"%s"}
                    """.formatted(bex.getErrorCode().name(), bex.getMessage());

            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return exchange.getResponse().setComplete();
    }
}
