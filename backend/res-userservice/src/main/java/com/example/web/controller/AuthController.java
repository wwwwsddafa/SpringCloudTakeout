package com.example.web.controller;

import com.example.configs.JwtTokenProvider;
import com.example.sevice.CaptchaService;
import com.example.sevice.UserService;
import com.example.web.vo.JwtUserInfo;
import com.example.web.vo.LoginVo;
import com.example.web.vo.RegisterVo;
import com.example.web.vo.ResultVo;
import com.example.web.vo.UserRole;
import com.example.web.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping
@Slf4j
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/user/register")
    public ResultVo register(@RequestBody RegisterVo registerVo) {
        if (!captchaService.verifyCaptcha(registerVo.getCaptchaKey(), registerVo.getCaptcha())) {
            return ResultVo.fail(
                    com.example.web.vo.ResultCode.CAPTCHA_ERROR.getCode(),
                    com.example.web.vo.ResultCode.CAPTCHA_ERROR.getMessage());
        }
        UserVo userVo = userService.register(registerVo);
        return ResultVo.success(userVo);
    }

    @PostMapping("/user/login")
    public ResultVo login(@RequestBody LoginVo loginVo) {
        if (!captchaService.verifyCaptcha(loginVo.getCaptchaKey(), loginVo.getCaptcha())) {
            return ResultVo.fail(
                    com.example.web.vo.ResultCode.CAPTCHA_ERROR.getCode(),
                    com.example.web.vo.ResultCode.CAPTCHA_ERROR.getMessage());
        }
        JwtUserInfo jwtUserInfo = userService.login(loginVo.getUsername(), loginVo.getPassword());
        String token = jwtTokenProvider.generateToken(jwtUserInfo);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", jwtUserInfo);
        return ResultVo.success(result);
    }

    // ======== 心月互联实现：同时支持GET和POST ========
    @RequestMapping(value = "/user/login/qq", method = {RequestMethod.GET, RequestMethod.POST})
    public ResultVo loginByQQ(@RequestParam("code") String code) {
        JwtUserInfo jwtUserInfo = userService.loginByQQ(code);
        String token = jwtTokenProvider.generateToken(jwtUserInfo);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", jwtUserInfo);
        return ResultVo.success(result);
    }

    // ======== QQ官方实现（已注释，保留用于后续切换） ========
    /*
    @PostMapping("/user/login/qq")
    public ResultVo loginByQQ(@RequestParam("code") String code) {
        JwtUserInfo jwtUserInfo = userService.loginByQQ(code);
        String token = jwtTokenProvider.generateToken(jwtUserInfo);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", jwtUserInfo);
        return ResultVo.success(result);
    }
    */

    @PostMapping("/user/logout")
    public ResultVo logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() != null) {
            String token = authentication.getCredentials().toString();
            userService.logout(token);
        }
        SecurityContextHolder.clearContext();
        return ResultVo.success("退出成功");
    }

    @GetMapping("/user/check")
    public ResultVo checkLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResultVo.fail(
                    com.example.web.vo.ResultCode.UNAUTHORIZED.getCode(),
                    com.example.web.vo.ResultCode.UNAUTHORIZED.getMessage());
        }
        JwtUserInfo userInfo = (JwtUserInfo) authentication.getPrincipal();
        return ResultVo.success(userInfo);
    }

    @GetMapping("/user/info")
    public ResultVo userInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResultVo.fail(
                    com.example.web.vo.ResultCode.UNAUTHORIZED.getCode(),
                    com.example.web.vo.ResultCode.UNAUTHORIZED.getMessage());
        }
        JwtUserInfo jwtUserInfo = (JwtUserInfo) authentication.getPrincipal();
        UserVo userVo = userService.getUserInfo(jwtUserInfo.getUserId());
        return ResultVo.success(userVo);
    }

    @GetMapping("/user/infoById")
    public ResultVo getUserInfoById(@RequestParam("userId") String userId) {
        UserVo userVo = userService.getUserInfo(userId);
        return ResultVo.success(userVo);
    }
}