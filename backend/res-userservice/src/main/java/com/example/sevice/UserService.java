package com.example.sevice;

import com.example.web.vo.JwtUserInfo;
import com.example.web.vo.RegisterVo;
import com.example.web.vo.UserVo;

public interface UserService {

    UserVo register(RegisterVo registerVo);

    JwtUserInfo login(String username, String password);

    JwtUserInfo loginByQQ(String code);

    void logout(String token);

    JwtUserInfo checkLogin(String token);

    UserVo getUserInfo(String userId);
}