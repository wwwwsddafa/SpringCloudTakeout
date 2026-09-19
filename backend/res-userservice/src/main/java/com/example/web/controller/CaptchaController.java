package com.example.web.controller;

import com.example.sevice.CaptchaService;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
@Slf4j
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @GetMapping("/user/captcha")
    public ResultVo captcha() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        return ResultVo.success(captcha);
    }
}