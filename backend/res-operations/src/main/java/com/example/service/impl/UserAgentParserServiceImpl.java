package com.example.service.impl;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.example.service.UserAgentParserService;
import com.example.web.vo.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class UserAgentParserServiceImpl implements UserAgentParserService {

    @Override
    public DeviceInfo parse(HttpServletRequest request) {
        DeviceInfo info = new DeviceInfo();
        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return info;
        }

        try {
            UserAgent agent = UserAgentUtil.parse(ua);

            info.setBrowser(agent.getBrowser() != null ? agent.getBrowser().toString() : null);
            info.setOs(agent.getOs() != null ? agent.getOs().toString() : null);

            if (agent.isMobile()) {
                info.setDeviceType("MOBILE");
            } else {
                info.setDeviceType("DESKTOP");
            }

            if (ua.contains("iPad") || ua.contains("Android") && !ua.contains("Mobile")) {
                info.setDeviceType("TABLET");
            }
        } catch (Exception e) {
            log.warn("User-Agent 解析失败: {}", ua, e);
        }
        return info;
    }
}