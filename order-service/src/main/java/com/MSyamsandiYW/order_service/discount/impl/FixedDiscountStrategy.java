package com.MSyamsandiYW.order_service.discount.impl;

import com.MSyamsandiYW.order_service.discount.Discount;
import com.MSyamsandiYW.order_service.discount.DiscountStrategy;
import com.MSyamsandiYW.order_service.order.Order;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component("FIXED")
public class FixedDiscountStrategy implements DiscountStrategy {

    @Override
    public Order apply(Order order, Discount discount) {
        double discountedPrice = Math.max(0, order.getTotalAmount() - discount.getValue());
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
