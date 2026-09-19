package com.example.sevice.impl;

import com.example.configs.QqOAuthConfig;
import com.example.exceptions.BizException;
import com.example.web.vo.QqUserInfo;
import com.example.web.vo.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class QqOAuthService {

    @Autowired
    private QqOAuthConfig qqOAuthConfig;

    @Autowired
    private RestTemplate restTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ======== 心月互联实现 ========
    public QqUserInfo getQqUserInfo(String code) {
        try {
            log.info("心月互联登录：接收到的code={}", code);
            // 直接调用心月互联获取用户信息接口
            String apiUrl = qqOAuthConfig.getApiBaseUrl() + "/get_user_info.php";
            URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("code", code)
                    .queryParam("token", qqOAuthConfig.getToken())
                    .build()
                    .toUri();

            log.info("心月互联请求URL: {}", uri);
            String response = restTemplate.getForObject(uri, String.class);
            log.info("心月互联获取用户信息响应: {}", response);

            if (!StringUtils.hasText(response)) {
                throw new BizException(ResultCode.QQ_LOGIN_ERROR);
            }

            JsonNode root = OBJECT_MAPPER.readTree(response);
            int ret = root.path("ret").asInt(-1);
            if (ret != 0) {
                String msg = root.path("msg").asText("");
                log.error("心月互联获取用户信息失败: ret={}, msg={}, 原始响应={}", ret, msg, response);
                throw new BizException(ResultCode.QQ_LOGIN_ERROR.getCode(),
                        "QQ登录失败: " + (StringUtils.hasText(msg) ? msg : "ret=" + ret));
            }

            return QqUserInfo.builder()
                    .openId(root.path("open_id").asText())
                    .nickname(root.path("nickname").asText("QQ用户"))
                    .avatar(root.path("figureurl_qq_2").asText(
                            root.path("figureurl_qq_1").asText("")))
                    .gender(root.path("gender").asText(""))
                    .build();
        } catch (BizException e) {
            // 业务异常直接透传，避免被外层 catch 二次包装导致错误码丢失
            throw e;
        } catch (Exception e) {
            log.error("心月互联获取用户信息异常", e);
            throw new BizException(ResultCode.QQ_LOGIN_ERROR.getCode(), "QQ登录失败: " + e.getMessage());
        }
    }

    // ======== QQ官方实现（已注释，保留用于后续切换） ========
    /*
    // 提取 access_token 的值
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("access_token=([^&]+)");
    // 提取 openid 的值。OpenID 是 QQ 用户的唯一标识。防止QQ号泄露
    private static final Pattern OPEN_ID_PATTERN = Pattern.compile("\"openid\"\\s*:\\s*\"([^\"]+)\"");

    public QqUserInfo getQqUserInfo(String code) {
        String accessToken = getAccessToken(code);
        String openId = getOpenId(accessToken);
        QqUserInfo userInfo = getUserInfo(accessToken, openId);
        userInfo.setOpenId(openId);
        return userInfo;
    }

    // 输入 QQ 授权回调时返回的授权码(code)，返回的是"当前正在登录的这个 QQ 号的个人信息"
    private String getAccessToken(String code) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(qqOAuthConfig.getTokenUrl())
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("client_id", qqOAuthConfig.getAppId())
                    .queryParam("client_secret", qqOAuthConfig.getAppSecret())
                    .queryParam("code", code)
                    .queryParam("redirect_uri", qqOAuthConfig.getRedirectUri())
                    .build()
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            log.debug("QQ获取access_token响应: {}", response);

            if (!StringUtils.hasText(response)) {
                throw new BizException(ResultCode.QQ_LOGIN_ERROR);
            }

            if (response.contains("error")) {
                log.error("QQ授权失败: {}", response);
                throw new BizException(ResultCode.QQ_LOGIN_ERROR);
            }

            Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new BizException(ResultCode.QQ_LOGIN_ERROR);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取QQ access_token异常", e);
            throw new BizException(ResultCode.QQ_LOGIN_ERROR.getCode(), "QQ登录失败: " + e.getMessage());
        }
    }

    // 输入：获取到的访问令牌，返回的是该 QQ 账号在当前应用下的唯一标识
    private String getOpenId(String accessToken) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(qqOAuthConfig.getOpenIdUrl())
                    .queryParam("access_token", accessToken)
                    .build()
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            log.debug("QQ获取openId响应: {}", response);

            if (!StringUtils.hasText(response)) {
                throw new BizException(ResultCode.QQ_LOGIN_ERROR);
            }

            Matcher matcher = OPEN_ID_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new BizException(ResultCode.QQ_LOGIN_ERROR);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取QQ openId异常", e);
            throw new BizException(ResultCode.QQ_LOGIN_ERROR.getCode(), "QQ登录失败: " + e.getMessage());
        }
    }

    // 输入：获取到的访问令牌和该 QQ 账号在当前应用下的唯一标识，返回的是该 QQ用户的个人信息
    private QqUserInfo getUserInfo(String accessToken, String openId) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(qqOAuthConfig.getUserInfoUrl())
                    .queryParam("access_token", accessToken)
                    .queryParam("oauth_consumer_key", qqOAuthConfig.getAppId())
                    .queryParam("openid", openId)
                    .build()
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            log.debug("QQ获取用户信息响应: {}", response);

            if (!StringUtils.hasText(response)) {
                throw new BizException(ResultCode.QQ_LOGIN_ERROR);
            }

            JsonNode root = OBJECT_MAPPER.readTree(response);
            int ret = root.path("ret").asInt(-1);
            if (ret != 0) {
                String msg = root.path("msg").asText("未知错误");
                log.error("QQ获取用户信息失败: ret={}, msg={}", ret, msg);
                throw new BizException(ResultCode.QQ_LOGIN_ERROR.getCode(), "获取QQ用户信息失败: " + msg);
            }

            return QqUserInfo.builder()
                    .nickname(root.path("nickname").asText("QQ用户"))
                    .avatar(root.path("figureurl_qq_2").asText(
                            root.path("figureurl_qq_1").asText("")))
                    .gender(root.path("gender").asText(""))
                    .build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取QQ用户信息异常", e);
            throw new BizException(ResultCode.QQ_LOGIN_ERROR.getCode(), "QQ登录失败: " + e.getMessage());
        }
    }
    */
}