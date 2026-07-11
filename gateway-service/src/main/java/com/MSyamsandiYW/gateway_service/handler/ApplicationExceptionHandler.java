package com.MSyamsandiYW.gateway_service.handler;

import com.MSyamsandiYW.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        log.error("Unhandled exception on path: {} - {}: {}", exchange.getRequest().getURI().getPath(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"code":"INTERNAL_EXCEPTION","message":"%s: %s"}
                """.formatted(ex.getClass().getSimpleName(), ex.getMessage() != null ? ex.getMessage().replace("\"", "'") : "unknown");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
