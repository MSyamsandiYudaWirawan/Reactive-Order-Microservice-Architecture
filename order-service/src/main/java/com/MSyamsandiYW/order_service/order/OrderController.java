package com.MSyamsandiYW.order_service.order;

import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import com.MSyamsandiYW.order_service.order.response.CreateOrderResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@Tag(name = "Order", description = "Order API")
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(code = CREATED)
    public Mono<ResponseEntity<CreateOrderResponse>> createOrder(
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestHeader("Authorization") String token,
            @RequestBody @Valid CreateOrderRequest request
            ){
        return orderService.createOrder(correlationId, token, request);
    }
}
