package com.MSyamsandiYW.order_service.properties;

public class AppConstant {
    public enum ORDER_STATUS {
        PENDING,
        WAITING_PAYMENT,
        PAID,
        COMPLETED,
        FAILED,
        REFUNDED
    }

    public static class TOPICS {
        public static final String STOCK_RESERVE_REQUESTED = "stock-reserve-requested";
        public static final String STOCK_RESERVE_COMPLETED = "stock-reserve-completed";
        public static final String PAYMENT_COMPLETED = "payment-completed";
        public static final String REFUND_COMPLETED = "order-refund-completed";
        public static final String ORDER_COMPLETED = "order-completed";
        public static final String ORDER_FAILED = "order-failed";
    }
}
