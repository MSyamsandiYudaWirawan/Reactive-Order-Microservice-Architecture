package com.MSyamsandiYW.order_service.discount.impl;

import com.MSyamsandiYW.order_service.discount.Discount;
import com.MSyamsandiYW.order_service.discount.DiscountStrategy;
import com.MSyamsandiYW.order_service.order.Order;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component("PERCENTAGE")
public class PercentageDiscountStrategy implements DiscountStrategy {
    @Override
    public Order apply(Order order, Discount discount) {
        double discountedPrice = order.getTotalAmount() * (1 - (discount.getValue() / 100));
        order.setTotalAmount(discountedPrice);
        return order;
    }

    @Override
    public boolean isApplicable(Order order, Discount discount) {
        ZonedDateTime now = ZonedDateTime.now();
        if (discount.getValidFrom().isAfter(now) || discount.getValidUntil().isBefore(now)) {
            return false;
        }
        return discount.getMaxUsage() > 0;
    }
}
