package com.example.constants;

public class RedisKeys {

    private RedisKeys() {}

    private static final String PREFIX = "ops:";

    private static final String PV_DAILY = PREFIX + "pv:daily:";

    private static final String UV_DAILY = PREFIX + "uv:daily:";

    private static final String PV_HOURLY = PREFIX + "pv:hourly:";

    private static final String ORDER_DAILY = PREFIX + "order:daily:";

    private static final String ORDER_DAILY_AMOUNT = PREFIX + "order:daily:amount:";

    private static final String ORDER_DAILY_PAID = PREFIX + "order:daily:paid:";

    private static final String ORDER_DAILY_PAID_AMOUNT = PREFIX + "order:daily:paidAmount:";

    private static final String SESSION_DAILY = PREFIX + "session:daily:";

    private static final String CART_DAILY = PREFIX + "cart:daily:";

    private static final String ORDER_DAILY_PAID_PRODUCT = PREFIX + "order:daily:paid:product:";

    private static final String ORDER_DAILY_PAID_AMOUNT_PRODUCT = PREFIX + "order:daily:paidAmount:product:";

    private static final String USER_DAILY_NEW = PREFIX + "user:daily:new:";

    private static final String DEVICE_DAILY = PREFIX + "device:daily:";

    private static final String ORDER_HOURLY = PREFIX + "order:hourly:";

    private static final String UV_ORDER_DAILY = PREFIX + "uv:order:daily:";

    private static final String LIKE_DAILY = PREFIX + "like:daily:";

    private static final String DISLIKE_DAILY = PREFIX + "dislike:daily:";

    public static String uvOrderDaily(String date) {
        return UV_ORDER_DAILY + date;
    }

    public static String likeDailyProduct(String date, String fid) {
        return LIKE_DAILY + date + ":" + fid;
    }

    public static String dislikeDailyProduct(String date, String fid) {
        return DISLIKE_DAILY + date + ":" + fid;
    }

    public static String deviceDaily(String date, String deviceType) {
        return DEVICE_DAILY + date + ":" + deviceType;
    }

    public static String deviceDailyPrefix(String date) {
        return DEVICE_DAILY + date;
    }

    public static String pvDaily(String date, String pageType) {
        return PV_DAILY + date + ":" + pageType;
    }

    public static String pvDailyProduct(String date, String fid) {
        return PV_DAILY + date + ":PRODUCT_DETAIL:" + fid;
    }

    public static String pvDailyTotal(String date) {
        return PV_DAILY + date + ":TOTAL";
    }

    public static String uvDaily(String date) {
        return UV_DAILY + date;
    }

    public static String uvDailyPageType(String date, String pageType) {
        return UV_DAILY + date + ":" + pageType;
    }

    public static String uvDailyProduct(String date, String fid) {
        return UV_DAILY + date + ":PRODUCT_DETAIL:" + fid;
    }

    public static String pvHourly(String date, int hour) {
        return PV_HOURLY + date + ":" + hour;
    }

    public static String orderDailyTotal(String date) {
        return ORDER_DAILY + date + ":total";
    }

    public static String orderDailyAmount(String date) {
        return ORDER_DAILY_AMOUNT + date;
    }

    public static String orderDailyPaid(String date) {
        return ORDER_DAILY_PAID + date;
    }

    public static String orderDailyPaidAmount(String date) {
        return ORDER_DAILY_PAID_AMOUNT + date;
    }

    public static String sessionDaily(String date) {
        return SESSION_DAILY + date;
    }

    public static String cartDailyProduct(String date, String fid) {
        return CART_DAILY + date + ":" + fid;
    }

    public static String orderDailyPaidProduct(String date, String fid) {
        return ORDER_DAILY_PAID_PRODUCT + date + ":" + fid;
    }

    public static String orderDailyPaidProductPrefix(String date) {
        return ORDER_DAILY_PAID_PRODUCT + date;
    }

    public static String orderDailyPaidAmountProduct(String date, String fid) {
        return ORDER_DAILY_PAID_AMOUNT_PRODUCT + date + ":" + fid;
    }

    public static String orderDailyPaidAmountProductPrefix(String date) {
        return ORDER_DAILY_PAID_AMOUNT_PRODUCT + date;
    }

    public static String userDailyNew(String date) {
        return USER_DAILY_NEW + date;
    }

    public static String orderHourly(String date, int hour) {
        return ORDER_HOURLY + date + ":" + hour;
    }
}