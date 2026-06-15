package com.MSyamsandiYW.payment_service.properties;


public class AppConstant {
    public enum PAYMENT_STATUS {
        PENDING,
        SUCCESS,
        FAILED,
        TIMEOUT,
        REFUNDED,
        REFUND_FAILED
    }
    public enum ORDER_STATUS {
        PENDING,
        WAITING_PAYMENT,
        PAID,
        COMPLETED,
        FAILED,
        REFUNDED
    }
    public static class TOPICS {
        public static final String PAYMENT_COMPLETED = "payment-completed"; //produce -> consumed by orchestrator-service
        public static final String PAYMENT_FAILED = "payment-failed"; //produce -> consumed by orchestrator-service
        public static final String ORDER_REFUND_COMPLETED = "order-refund-completed"; //produce -> consumed by order-service
        public static final String ORDER_REFUND_FAILED = "order-refund-failed";
        public static final String REFUND_REQUESTED = "refund-requested"; //consume <- from orchestrator-service
        public static final String PAYMENT_TIMEOUT = "payment-timeout"; //consume <- from orchestrator-service
    }
}
