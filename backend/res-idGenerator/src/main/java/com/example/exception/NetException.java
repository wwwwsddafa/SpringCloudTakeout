package com.example.exception;

public class NetException extends RuntimeException {
    public NetException(String message) {
        super(message);
    }
}