package com.example.sevice;

import java.util.Map;

public interface CaptchaService {

    Map<String, String> generateCaptcha();

    boolean verifyCaptcha(String captchaKey, String captchaCode);
}