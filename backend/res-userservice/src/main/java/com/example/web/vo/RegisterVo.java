package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterVo {

    private String username;

    private String password;

    private String email;

    private String captcha;

    private String captchaKey;
}