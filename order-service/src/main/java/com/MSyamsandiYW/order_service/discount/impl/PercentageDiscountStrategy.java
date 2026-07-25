package com.MSyamsandiYW.order_service.discount.impl;

import com.MSyamsandiYW.order_service.discount.Discount;
import com.MSyamsandiYW.order_service.discount.DiscountStrategy;
import com.MSyamsandiYW.order_service.order.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component("PERCENTAGE")
public class PercentageDiscountStrategy implements DiscountStrategy {
    @Override
    public Order apply(Order order, Discount discount) {
        //check max cap
        if(discount.getMaximumOrderValue() != null && discount.getMaximumOrderValue().compareTo(order.getTotalAmount()) < 0){
            BigDecimal totalAmount = discount.getMaximumOrderValue();
            BigDecimal remainingTotal = order.getTotalAmount().subtract(discount.getMaximumOrderValue());
            BigDecimal discountedPrice = totalAmount.multiply(BigDecimal.ONE.subtract(discount.getValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
            order.setTotalAmount(discountedPrice.add(remainingTotal).setScale(2, RoundingMode.HALF_UP));
            return order;
        }

        BigDecimal discountedPrice = order.getTotalAmount().multiply(BigDecimal.ONE.subtract(discount.getValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
        order.setTotalAmount(discountedPrice.setScale(2, RoundingMode.HALF_UP));
        order.setDiscountCode(discount.getCode());
        return order;
    }

    @Override
    public boolean isApplicable(Order order, Discount discount) {
        Instant now = Instant.now();
        if (discount.getValidFrom().isAfter(now) || discount.getValidUntil().isBefore(now)) {
            return false;
        }
        if(discount.getMinimumOrderValue() != null && discount.getMinimumOrderValue().compareTo(order.getTotalAmount()) > 0){
            return false;
        }
        return discount.getMaxUsage() > 0;
    }
}
