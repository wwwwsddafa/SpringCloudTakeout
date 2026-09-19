package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtUserInfo {

    private String userId;

    private String username;

    private UserRole role;
//   jti (JWT ID) 是JWT标准中的一个声明字段,用于唯一标识一个JWT令牌。 用于防止重复登录
    private String jti;
}