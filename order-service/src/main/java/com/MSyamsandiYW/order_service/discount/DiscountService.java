package com.MSyamsandiYW.order_service.discount;

import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order_item.OrderItem;
import reactor.core.publisher.Mono;

public interface DiscountService {

    Mono<Order> apply(OrderItem orderItem, String couponCode);
    Mono<Boolean> isApplicable(OrderItem orderItem,String couponCode);
}
