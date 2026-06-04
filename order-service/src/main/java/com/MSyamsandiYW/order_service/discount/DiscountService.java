package com.MSyamsandiYW.order_service.discount;

import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import reactor.core.publisher.Mono;

public interface DiscountService {
    Mono<Order> apply(CreateOrderRequest request, Order order);
}
