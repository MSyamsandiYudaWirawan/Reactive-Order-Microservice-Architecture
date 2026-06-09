package com.MSyamsandiYW.inventory_service.properties;

public class AppConstant {

    public enum RESERVATION_STATUS {
        RESERVED,
        OUT_OF_STOCK,
        RELEASED,
        DEDUCTED
    }
    public static class TOPICS{

    public static final String STOCK_RESERVE_REQUESTED = "stock-reserve-requested";  // consumes (from order-service)
    public static final String STOCK_RESERVE_COMPLETED = "stock-reserve-completed"; // produces (stock reserved OK)
    public static final String OUT_OF_STOCK = "out-of-stock"; // produces (stock insufficient)
    public static final String RELEASE_STOCK = "release-stock"; // consumes (from fulfillment-service — compensation)
    public static final String DEDUCT_STOCK = "deduct-stock"; // consumes (from fulfillment-service — confirm sold)

    }

}
