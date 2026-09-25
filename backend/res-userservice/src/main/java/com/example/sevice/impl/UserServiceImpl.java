package com.example.sevice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.api.OpsEventApi;
import com.example.bean.ResAdmin;
import com.example.bean.ResUser;
import com.example.dao.mapper.ResAdminMapper;
import com.example.dao.mapper.ResUserMapper;
import com.example.sevice.UserService;
import com.example.exceptions.BizException;
import com.example.web.vo.EmailMessage;
import com.example.web.vo.JwtUserInfo;
import com.example.web.vo.QqUserInfo;
import com.example.web.vo.RegisterVo;
import com.example.web.vo.ResultCode;
import com.example.web.vo.UserRole;
import com.example.web.vo.UserVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private ResUserMapper resUserMapper;

    @Autowired
    private ResAdminMapper resAdminMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private QqOAuthService qqOAuthService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OpsEventApi opsEventApi;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    @Override
    public UserVo register(RegisterVo registerVo) {
        if (registerVo == null || !StringUtils.hasText(registerVo.getUsername())
                || !StringUtils.hasText(registerVo.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }

        // 事务内只做数据库操作，连接占用时间从几百毫秒降到几毫秒
        UserVo userVo = transactionTemplate.execute(status -> {
            Long count = resUserMapper.selectCount(
                    new LambdaQueryWrapper<ResUser>()
                            .eq(ResUser::getUsername, registerVo.getUsername()));
            if (count > 0) {
                throw new BizException(ResultCode.USERNAME_EXISTS);
            }

            ResUser user = new ResUser();
            user.setUserid(UUID.randomUUID().toString().replace("-", ""));
            user.setUsername(registerVo.getUsername());
            user.setPwd(passwordEncoder.encode(registerVo.getPassword()));
            user.setEmail(registerVo.getEmail());
            user.setCreateTime(LocalDateTime.now());

            resUserMapper.insert(user);
            log.info("新用户注册成功: {}", user.getUsername());

            return UserVo.builder()
                    .userId(user.getUserid())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(UserRole.USER.name())
                    .build();
        });

        // 事务提交、连接归还后再做远程调用
        try {
            opsEventApi.trackUserRegister();
        } catch (Exception e) {
            log.warn("埋点调用失败，不影响注册: {}", e.getMessage());
        }

        if (StringUtils.hasText(registerVo.getEmail())) {
            sendEmailMessage(EmailMessage.TYPE_REGISTER, userVo.getUserId(),
                    registerVo.getEmail(), registerVo.getUsername(), null);
        }

        return userVo;
    }

    @Override
    @Transactional(readOnly = true)
    public JwtUserInfo login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }

        ResUser user = resUserMapper.selectOne(
                new LambdaQueryWrapper<ResUser>()
                        .eq(ResUser::getUsername, username));
        if (user != null) {
            if (!passwordEncoder.matches(password, user.getPwd())) {
                throw new BizException(ResultCode.PASSWORD_ERROR);
            }
            log.info("用户登录成功: {}", username);
            // 发送登录提醒邮件
            if (StringUtils.hasText(user.getEmail())) {
                Map<String, Object> params = new HashMap<>();
                params.put("loginIp", getClientIp());
                sendEmailMessage(EmailMessage.TYPE_LOGIN, user.getUserid(), user.getEmail(), user.getUsername(), params);
            }
            return buildJwtUserInfo(user.getUserid(), user.getUsername(), UserRole.USER);
        }

        ResAdmin admin = resAdminMapper.selectOne(
                new LambdaQueryWrapper<ResAdmin>()
                        .eq(ResAdmin::getRaname, username));
        if (admin != null) {
            if (!passwordEncoder.matches(password, admin.getRapwd())) {
                throw new BizException(ResultCode.PASSWORD_ERROR);
            }
            log.info("管理员登录成功: {}", username);
            return buildJwtUserInfo(admin.getRaid(), admin.getRaname(), UserRole.ADMIN);
        }

        throw new BizException(ResultCode.USER_NOT_FOUND);
    }

    @Override
    @Transactional
    public JwtUserInfo loginByQQ(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ResultCode.QQ_LOGIN_ERROR);
        }

        QqUserInfo qqUserInfo = qqOAuthService.getQqUserInfo(code);
        String openId = qqUserInfo.getOpenId();
        log.info("QQ登录: openId={}, nickname={}", openId, qqUserInfo.getNickname());

        ResUser user = resUserMapper.selectOne(
                new LambdaQueryWrapper<ResUser>()
                        .eq(ResUser::getOpenId, openId));
        if (user != null) {
            if (!StringUtils.hasText(user.getAvatar())) {
                user.setAvatar(qqUserInfo.getAvatar());
                resUserMapper.updateById(user);
            }
            log.info("QQ用户已绑定，登录成功: {}", user.getUsername());
            return buildJwtUserInfo(user.getUserid(), user.getUsername(), UserRole.USER);
        }

        String nickname = qqUserInfo.getNickname();
        String username = generateUniqueUsername(nickname);
        ResUser newUser = new ResUser();
        newUser.setUserid(UUID.randomUUID().toString().replace("-", ""));
        newUser.setUsername(username);
        newUser.setPwd(passwordEncoder.encode(openId));
        newUser.setOpenId(openId);
        newUser.setAvatar(qqUserInfo.getAvatar());
        newUser.setCreateTime(LocalDateTime.now());

        resUserMapper.insert(newUser);
        log.info("QQ用户首次登录，自动注册: username={}, openId={}", username, openId);

        return buildJwtUserInfo(newUser.getUserid(), newUser.getUsername(), UserRole.USER);
    }

    private String generateUniqueUsername(String nickname) {
        String baseName = "qq_" + (StringUtils.hasText(nickname) ? nickname : "user");
        String username = baseName;
        int suffix = 1;
        while (resUserMapper.selectCount(
                new LambdaQueryWrapper<ResUser>().eq(ResUser::getUsername, username)) > 0) {
            username = baseName + "_" + suffix;
            suffix++;
        }
        return username;
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        long ttl = 7200;
        redisTemplate.opsForValue().set(
                TOKEN_BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.SECONDS);
        log.info("用户退出登录，Token已加入黑名单");
    }

    @Override
    public JwtUserInfo checkLogin(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        String blacklisted = redisTemplate.opsForValue().get(TOKEN_BLACKLIST_PREFIX + token);
        if (StringUtils.hasText(blacklisted)) {
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public UserVo getUserInfo(String userId) {
        ResUser user = resUserMapper.selectById(userId);
        if (user == null) {
            ResAdmin admin = resAdminMapper.selectById(userId);
            if (admin != null) {
                return UserVo.builder()
                        .userId(admin.getRaid())
                        .username(admin.getRaname())
                        .role(UserRole.ADMIN.name())
                        .build();
            }
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return UserVo.builder()
                .userId(user.getUserid())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(UserRole.USER.name())
                .build();
    }

    private JwtUserInfo buildJwtUserInfo(String userId, String username, UserRole role) {
        return JwtUserInfo.builder()
                .userId(userId)
                .username(username)
                .role(role)
                .jti(UUID.randomUUID().toString().replace("-", ""))
                .build();
    }

    private void sendEmailMessage(String type, String userId, String email, String username, Map<String, Object> params) {
        try {
            EmailMessage message = EmailMessage.builder()
                    .type(type)
                    .userId(userId)
                    .email(email)
                    .username(username)
                    .params(params)
                    .build();
            rabbitTemplate.convertAndSend(EmailMessage.EMAIL_EXCHANGE, EmailMessage.EMAIL_ROUTING_KEY, message);
            log.info("邮件消息已发送: type={}, email={}", type, email);
        } catch (Exception e) {
            log.error("发送邮件消息失败: type={}, email={}", type, email, e);
        }
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}