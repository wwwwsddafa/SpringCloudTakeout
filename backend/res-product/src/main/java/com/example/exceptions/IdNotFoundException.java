package com.example.exceptions;

public class IdNotFoundException extends RuntimeException {
    public IdNotFoundException()
    {
super("商品id找不到");
    }
}
