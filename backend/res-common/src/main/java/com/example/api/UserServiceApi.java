package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "userservice")
public interface UserServiceApi {

    @PostMapping("/user/register")
    ResultVo register(@RequestBody Map<String, String> registerInfo);

    @PostMapping("/user/login")
    ResultVo login(@RequestBody Map<String, String> loginInfo);

    @PostMapping("/user/login/qq")
    ResultVo loginByQQ(@RequestParam("code") String code);

    @PostMapping("/user/logout")
    ResultVo logout(@RequestParam("token") String token);

    @GetMapping("/user/check")
    ResultVo checkLogin(@RequestParam("token") String token);

    @GetMapping("/user/captcha")
    ResultVo captcha();

    @GetMapping("/user/info")
    ResultVo userInfo(@RequestParam("token") String token);

    @GetMapping("/user/infoById")
    ResultVo getUserInfoById(@RequestParam("userId") String userId);
}