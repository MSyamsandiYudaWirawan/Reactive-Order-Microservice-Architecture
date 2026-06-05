package com.MSyamsandiYW.order_service.discount.impl;

import com.MSyamsandiYW.order_service.discount.DiscountRepository;
import com.MSyamsandiYW.order_service.discount.DiscountService;
import com.MSyamsandiYW.order_service.discount.DiscountStrategy;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final Map<String, DiscountStrategy> discountStrategy;

    @Override
    public Mono<Order> apply(CreateOrderRequest request, Order order) {
        if (request.getDiscountCode() == null || request.getDiscountCode().isEmpty()) {
            return Mono.just(order);
        }

        return discountRepository.findByCode(request.getDiscountCode())
                .flatMap(discount -> {
                    DiscountStrategy strategy = discountStrategy.get(discount.getDiscountType());
                    if (strategy != null && strategy.isApplicable(order, discount)) {
                        Order discounted = strategy.apply(order, discount);
                        discounted.setDiscountCode(discount.getCode());
                        discount.setMaxUsage(discount.getMaxUsage() - 1);
                        return discountRepository.save(discount).thenReturn(discounted);
                    }
                    return Mono.just(order);
                });


    }
}
