package com.example.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "qq.oauth")
public class QqOAuthConfig {

    // ======== 心月互联配置 ========
    // 心月互联申请的token
    private String token;

    // 心月互联API基础地址
    private String apiBaseUrl = "https://qq.wch666.com/api";

    // 前端地址，QQ登录成功后重定向回前端
    private String frontendUrl = "http://localhost:5173";

    // ======== QQ官方配置（已注释，保留用于后续切换） ========
    /*
    // QQ开放平台分配的应用ID
    private String appId;

    // QQ开放平台分配的应用密钥（需保密）
    private String appSecret;

    // QQ授权成功后的回调地址
    private String redirectUri;

    // QQ授权页面地址，用户在此页面点击授权登录
    private String authorizeUrl = "https://graph.qq.com/oauth2.0/authorize";

    // 用授权码(code)换取Access Token的接口地址
    private String tokenUrl = "https://graph.qq.com/oauth2.0/token";

    // 获取用户OpenID（唯一标识）的接口地址
    private String openIdUrl = "https://graph.qq.com/oauth2.0/me";

    // 获取用户详细信息（昵称、头像等）的接口地址
    private String userInfoUrl = "https://graph.qq.com/user/get_user_info";
    */
}