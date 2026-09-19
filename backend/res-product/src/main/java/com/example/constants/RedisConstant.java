package com.example.constants;


/**
 * Redis key constants
 * Redis中所有的键值对的  键的定义
 */
public class RedisConstant {
    /** food list cache key */
    public static final String FOOD_LIST_KEY = "restaurant:food:list";
    /** food detail cache key prefix */
    public static final String FOOD_DETAIL_KEY = "restaurant:food:detail:";
    /** cart key prefix, suffix = userId */
    public static final String CART_KEY = "restaurant:cart:";
    /** captcha key prefix, suffix = email */
    public static final String CAPTCHA_KEY = "restaurant:captcha:";
    /** food cache ttl (seconds) */
    public static final long FOOD_CACHE_TTL = 30 * 60;
    /** captcha ttl (seconds) */
    public static final long CAPTCHA_TTL = 5 * 60;
}