package com.example.ticket;

public enum TicketCategory {
    REFUND("退款售后"),
    LOGISTICS("物流配送"),
    PRODUCT("商品咨询"),
    COMPLAINT("投诉建议"),
    ACCOUNT("账号问题"),
    PAYMENT("支付问题"),
    COUPON("优惠券"),
    OTHER("其他");

    private final String description;

    TicketCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}