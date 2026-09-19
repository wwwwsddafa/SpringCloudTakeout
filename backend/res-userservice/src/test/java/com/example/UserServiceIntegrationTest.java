package com.example;

import com.example.configs.JwtTokenProvider;
import com.example.sevice.CaptchaService;
import com.example.sevice.UserService;
import com.example.web.vo.JwtUserInfo;
import com.example.web.vo.LoginVo;
import com.example.web.vo.RegisterVo;
import com.example.web.vo.UserRole;
import com.example.web.vo.UserVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static String testToken;
    private static String testUserId;
    private static String testUsername = "test_user_" + System.currentTimeMillis();

    @Test
    @Order(1)
    public void test01GenerateCaptcha() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        Assertions.assertNotNull(captcha);
        Assertions.assertNotNull(captcha.get("captchaKey"));
        Assertions.assertNotNull(captcha.get("captchaImage"));
        System.out.println("=== 验证码生成成功 ===");
        System.out.println("captchaKey: " + captcha.get("captchaKey"));
    }

    @Test
    @Order(2)
    public void test02Register() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaKey = captcha.get("captchaKey");
        String captchaCode = redisTemplate.opsForValue().get("captcha:" + captchaKey);

        RegisterVo registerVo = RegisterVo.builder()
                .username(testUsername)
                .password("123456")
                .email(testUsername + "@test.com")
                .captcha(captchaCode)
                .captchaKey(captchaKey)
                .build();

        UserVo userVo = userService.register(registerVo);
        Assertions.assertNotNull(userVo);
        Assertions.assertEquals(testUsername, userVo.getUsername());
        Assertions.assertEquals(UserRole.USER.name(), userVo.getRole());
        testUserId = userVo.getUserId();
        System.out.println("=== 注册成功 ===");
        System.out.println("userId: " + testUserId);
        System.out.println("username: " + userVo.getUsername());
    }

    @Test
    @Order(3)
    public void test03LoginWithCaptcha() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaKey = captcha.get("captchaKey");
        String captchaCode = redisTemplate.opsForValue().get("captcha:" + captchaKey);

        JwtUserInfo jwtUserInfo = userService.login(testUsername, "123456");
        Assertions.assertNotNull(jwtUserInfo);
        Assertions.assertEquals(testUsername, jwtUserInfo.getUsername());
        Assertions.assertEquals(UserRole.USER, jwtUserInfo.getRole());

        testToken = jwtTokenProvider.generateToken(jwtUserInfo);
        Assertions.assertNotNull(testToken);
        System.out.println("=== 登录成功 ===");
        System.out.println("Token: " + testToken);
        System.out.println("userId: " + jwtUserInfo.getUserId());
        System.out.println("role: " + jwtUserInfo.getRole());
    }

    @Test
    @Order(4)
    public void test04ValidateToken() {
        Assertions.assertNotNull(testToken, "请先执行登录测试");
        boolean valid = jwtTokenProvider.validateToken(testToken);
        Assertions.assertTrue(valid, "Token校验失败");
        JwtUserInfo userInfo = jwtTokenProvider.parseToken(testToken);
        Assertions.assertEquals(testUsername, userInfo.getUsername());
        Assertions.assertEquals(UserRole.USER, userInfo.getRole());
        System.out.println("=== Token校验成功 ===");
        System.out.println("解析出的用户: " + userInfo.getUsername());
        System.out.println("解析出的角色: " + userInfo.getRole());
    }

    @Test
    @Order(5)
    public void test05CheckLogin() {
        Assertions.assertNotNull(testToken, "请先执行登录测试");
        userService.checkLogin(testToken);
        System.out.println("=== 检查登录状态成功：Token有效 ===");
    }

    @Test
    @Order(6)
    public void test06GetUserInfo() {
        Assertions.assertNotNull(testUserId, "请先执行注册测试");
        UserVo userVo = userService.getUserInfo(testUserId);
        Assertions.assertNotNull(userVo);
        Assertions.assertEquals(testUsername, userVo.getUsername());
        System.out.println("=== 获取用户信息成功 ===");
        System.out.println("userId: " + userVo.getUserId());
        System.out.println("username: " + userVo.getUsername());
        System.out.println("email: " + userVo.getEmail());
        System.out.println("role: " + userVo.getRole());
    }

    @Test
    @Order(7)
    public void test07Logout() {
        Assertions.assertNotNull(testToken, "请先执行登录测试");
        userService.logout(testToken);
        System.out.println("=== 退出登录成功 ===");

        String blacklisted = redisTemplate.opsForValue().get("token:blacklist:" + testToken);
        Assertions.assertEquals("1", blacklisted, "Token应加入黑名单");
        System.out.println("=== Token已加入黑名单 ===");
    }

    @Test
    @Order(8)
    public void test08TokenInBlacklist() {
        Assertions.assertNotNull(testToken, "请先执行登录测试");
        Assertions.assertThrows(RuntimeException.class, () -> {
            userService.checkLogin(testToken);
        }, "黑名单中的Token应抛出异常");
        System.out.println("=== 黑名单校验通过：Token已失效 ===");
    }

    @Test
    @Order(9)
    public void test09RoleBasedAccess() {
        JwtUserInfo guestInfo = JwtUserInfo.builder()
                .userId("guest001")
                .username("guest")
                .role(UserRole.GUEST)
                .jti("test-jti-guest")
                .build();
        String guestToken = jwtTokenProvider.generateToken(guestInfo);
        JwtUserInfo parsed = jwtTokenProvider.parseToken(guestToken);
        Assertions.assertEquals(UserRole.GUEST, parsed.getRole());
        System.out.println("=== 角色权限测试 ===");
        System.out.println("GUEST 角色 Token: " + guestToken);
        System.out.println("序号  | 角色    | 权限");
        System.out.println("------|---------|------------------");
        System.out.println("  1   | GUEST   | 商品浏览、查看详情");
        System.out.println("  2   | USER    | 加购物车、下单");
        System.out.println("  3   | ADMIN   | 上下架商品");
    }
}