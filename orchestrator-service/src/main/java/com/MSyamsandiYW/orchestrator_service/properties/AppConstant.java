package com.MSyamsandiYW.orchestrator_service.properties;

public class AppConstant {

    public enum STOCK_STATUS {

        RESERVED, OUT_OF_STOCK

    }

    public enum PAYMENT_STATUS {
        INITIATED, PAID, FAILED
    }

    public enum SAGA_STATUS {
        IN_PROGRESS, COMPLETED, COMPENSATING, FAILED

    }

    public static class TOPICS {
        //consume
        public static final String STOCK_RESERVE_COMPLETED = "stock-reserve-completed";
        public static final String PAYMENT_INITIATED = "payment-initiated";
        public static final String PAYMENT_COMPLETED = "payment-completed";
        public static final String PAYMENT_FAILED = "payment-failed";
        public static final String OUT_OF_STOCK = "out-of-stock";

        //produce
        public static final String DEDUCT_STOCK = "deduct-stock";
        public static final String RELEASE_STOCK = "release-stock";
        public static final String REFUND_REQUESTED = "refund-requested";
        public static final String ORDER_COMPLETED = "order-completed";
        public static final String ORDER_EXPIRED = "order-expired";
    }
}
