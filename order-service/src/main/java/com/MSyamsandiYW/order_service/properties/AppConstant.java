package com.MSyamsandiYW.order_service.properties;

public class AppConstant {
    public enum ORDER_STATUS {
        PENDING,
        WAITING_PAYMENT,
        PAID,
        COMPLETED,
        REFUNDED,
        OUT_OF_STOCK,
        PAYMENT_FAILED,
        EXPIRED,
        TIMEOUT,
        REFUND_FAILED
    }
    public enum DISCOUNT_TYPE {
        PERCENTAGE,
        FIXED
    }

    public static class TOPICS {
        public static final String STOCK_RESERVE_REQUESTED = "stock-reserve-requested";
        public static final String STOCK_RESERVE_COMPLETED = "stock-reserve-completed";
        public static final String PAYMENT_COMPLETED = "payment-completed";
        public static final String PAYMENT_FAILED = "payment-failed";
        public static final String REFUND_COMPLETED = "order-refund-completed";
        public static final String REFUND_FAILED = "order-refund-failed";
        public static final String ORDER_COMPLETED = "order-completed";
        public static final String OUT_OF_STOCK = "out-of-stock";
        public static final String ORDER_EXPIRED = "order-expired";
        public static final String ORDER_TIMEOUT = "order-timeout";
    }
}
