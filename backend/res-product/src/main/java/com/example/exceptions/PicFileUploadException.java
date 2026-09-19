package com.example.exceptions;



/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026‑08‑17 14:27
 */
public class PicFileUploadException extends RuntimeException{
    public PicFileUploadException(String message){
        super(message);
    }
}