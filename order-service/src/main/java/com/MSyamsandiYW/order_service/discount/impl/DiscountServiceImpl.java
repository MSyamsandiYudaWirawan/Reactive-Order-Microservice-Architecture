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
                    if (strategy == null || !strategy.isApplicable(order, discount)) {
                        return Mono.empty();
                    }
                    return discountRepository.updateDiscount(discount.getCode())
                            .filter(rowsUpdated -> rowsUpdated > 0)
                            .map(rowsUpdated -> strategy.apply(order, discount));
                })
                .defaultIfEmpty(order);
    }
}
