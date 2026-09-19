package com.example.security;

import com.example.bean.ResAdmin;
import com.example.bean.ResUser;
import com.example.dao.mapper.ResAdminMapper;
import com.example.dao.mapper.ResUserMapper;
import com.example.web.vo.UserRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private ResUserMapper resUserMapper;

    @Autowired
    private ResAdminMapper resAdminMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ① 先查普通用户表
        ResUser user = resUserMapper.selectOne(
                new LambdaQueryWrapper<ResUser>()
                        .eq(ResUser::getUsername, username));
        if (user != null) {
            // 找到了 → 返回普通用户身份
            return User.builder()
                    .username(user.getUsername())
                    .password(user.getPwd())
                    .authorities(UserRole.USER.name())
                    .build();
        }

        ResAdmin admin = resAdminMapper.selectOne(
                new LambdaQueryWrapper<ResAdmin>()
                        .eq(ResAdmin::getRaname, username));
        if (admin != null) {
            return User.builder()
                    .username(admin.getRaname())
                    .password(admin.getRapwd())
                    .authorities(UserRole.ADMIN.name())
                    .build();
        }

        throw new UsernameNotFoundException("用户不存在: " + username);
    }
}