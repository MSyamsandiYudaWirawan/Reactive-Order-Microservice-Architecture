package com.MSyamsandiYW.inventory_service.config.properties;

public class AppConstant {

    public enum TOPICS {
        STOCK_RESERVE_REQUESTED,   // consumes (from order-service)
        STOCK_RESERVE_COMPLETED,   // produces (stock reserved OK)
        OUT_OF_STOCK,              // produces (stock insufficient)
        RELEASE_STOCK,             // consumes (from fulfillment-service — compensation)
        DEDUCT_STOCK               // consumes (from fulfillment-service — confirm sold)
    }

}
