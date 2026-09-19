package com.example.exceptions;


/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026‑08‑16 16:56
 */
public class MinioUploadException extends RuntimeException {
    public MinioUploadException(String message) {
        super(message);
    }
}