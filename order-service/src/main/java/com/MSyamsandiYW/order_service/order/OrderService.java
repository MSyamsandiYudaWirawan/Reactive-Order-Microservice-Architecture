package com.MSyamsandiYW.order_service.order;

import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import com.MSyamsandiYW.order_service.order.response.CreateOrderResponse;
import com.MSyamsandiYW.order_service.order.response.GetUserOrdersResponse;
import com.MSyamsandiYW.order_service.order.response.GetStatusOrderResponse;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface OrderService {
    Mono<ResponseEntity<CreateOrderResponse>> createOrder(String correlationId,String token,CreateOrderRequest request);
    Mono<ResponseEntity<GetStatusOrderResponse>> getStatusOrder(String token, String transactionId);
    Mono<ResponseEntity<GetUserOrdersResponse>> getUserOrders(String token);
}
