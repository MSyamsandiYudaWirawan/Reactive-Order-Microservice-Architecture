package com.MSyamsandiYW.order_service.discount.impl;

import com.MSyamsandiYW.order_service.discount.DiscountRepository;
import com.MSyamsandiYW.order_service.discount.DiscountService;
import com.MSyamsandiYW.order_service.discount.DiscountStrategy;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.request.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final Map<String, DiscountStrategy> discountStrategy;

    @Override
    public Mono<Order> apply(CreateOrderRequest request, Order order) {
        if (request.getDiscountCode() == null || request.getDiscountCode().isEmpty()) {
            log.info("No discount code provided, skipping discount application");
            return Mono.just(order);
        }

        log.info("Applying discount code: {}", request.getDiscountCode());
        // find discount
        return discountRepository.findByCode(request.getDiscountCode())
                .flatMap(discount -> {
                    DiscountStrategy strategy = discountStrategy.get(discount.getDiscountType());
                    // apply discount if valid
                    if (strategy != null && strategy.isApplicable(order, discount)) {
                        Order discounted = strategy.apply(order, discount);
                        discounted.setDiscountCode(discount.getCode());
                        //decrement discount usage
                        discount.setMaxUsage(discount.getMaxUsage() - 1);
                        log.info("Discount applied successfully, type: {}, code: {}", discount.getDiscountType(), discount.getCode());
                        return discountRepository.save(discount).thenReturn(discounted);
                    }
                    log.info("Discount not applicable, type: {}, code: {}", discount.getDiscountType(), discount.getCode());
                    return Mono.just(order);
                })
                .doOnError(e -> log.error("Error applying discount code: {}", request.getDiscountCode(), e));
    }
}
