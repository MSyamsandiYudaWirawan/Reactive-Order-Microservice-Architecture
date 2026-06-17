package com.MSyamsandiYW.order_service.discount;

import com.MSyamsandiYW.order_service.order.Order;

public interface DiscountStrategy {
    Order apply(Order order, Discount discount);
    boolean isApplicable(Order order, Discount discount);
}
