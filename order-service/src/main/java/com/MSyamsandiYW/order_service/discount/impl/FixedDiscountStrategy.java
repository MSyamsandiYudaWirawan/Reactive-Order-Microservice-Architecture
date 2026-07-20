package com.MSyamsandiYW.order_service.discount.impl;

import com.MSyamsandiYW.order_service.discount.Discount;
import com.MSyamsandiYW.order_service.discount.DiscountStrategy;
import com.MSyamsandiYW.order_service.order.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Component("FIXED")
public class FixedDiscountStrategy implements DiscountStrategy {

    @Override
    public Order apply(Order order, Discount discount) {

        //check max cap
        if(discount.getMaximumOrderValue() != null && discount.getMaximumOrderValue().compareTo(order.getTotalAmount()) < 0){
            BigDecimal totalAmount = discount.getMaximumOrderValue();
            BigDecimal remainingTotal = order.getTotalAmount().subtract(discount.getMaximumOrderValue());
            BigDecimal discountedPrice = totalAmount.subtract(discount.getValue()).max(BigDecimal.ZERO);
            order.setTotalAmount(discountedPrice.add(remainingTotal));
            return order;
        }

        BigDecimal discountedPrice = order.getTotalAmount().subtract(discount.getValue()).max(BigDecimal.ZERO);
        order.setTotalAmount(discountedPrice);
        order.setDiscountCode(discount.getCode());
        return order;
    }

    @Override
    public boolean isApplicable(Order order, Discount discount) {
        ZonedDateTime now = ZonedDateTime.now();
        if (discount.getValidFrom().isAfter(now) || discount.getValidUntil().isBefore(now)) {
            return false;
        }
        if(discount.getMinimumOrderValue() != null && discount.getMinimumOrderValue().compareTo(order.getTotalAmount()) > 0){
            return false;
        }
        return discount.getMaxUsage() > 0;
    }
}
