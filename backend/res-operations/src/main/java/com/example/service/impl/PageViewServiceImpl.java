package com.example.service.impl;

import com.example.constants.RedisKeys;
import com.example.service.PageViewService;
import com.example.service.PvPersistService;
import com.example.service.UserAgentParserService;
import com.example.web.vo.DeviceInfo;
import com.example.web.vo.PageViewVo;
import com.example.web.vo.ResultVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PageViewServiceImpl implements PageViewService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PvPersistService pvPersistService;

    @Autowired
    private UserAgentParserService userAgentParserService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int REDIS_TTL_HOURS = 48;

    @Override
    public ResultVo track(HttpServletRequest request, PageViewVo vo) {
        String userId = request.getHeader("X-User-Id");
        if (!StringUtils.hasText(userId)) {
            userId = userIdFromToken(request.getHeader("Authorization"));
        }
        String sessionId = request.getHeader("X-Session-Id");
        String ip = getClientIp(request);
        DeviceInfo device = userAgentParserService.parse(request);

        String pageType = StringUtils.hasText(vo.getPageType()) ? vo.getPageType() : "OTHER";
        String pageUrl = vo.getPageUrl();
        String fid = vo.getFid();
        String referer = vo.getReferer();
        Integer staySeconds = vo.getStaySeconds();

        String today = LocalDate.now().format(DATE_FMT);
        int hour = LocalTime.now().getHour();

        incrementRedisCounters(today, hour, pageType, fid, userId, sessionId, device);

        pvPersistService.asyncSave(userId, sessionId, pageUrl, pageType, fid, device, ip, referer, staySeconds);

        return ResultVo.success("ok");
    }

    private void incrementRedisCounters(String today, int hour, String pageType, String fid, String userId, String sessionId, DeviceInfo device) {
        String totalKey = RedisKeys.pvDailyTotal(today);
        redisTemplate.opsForValue().increment(totalKey);
        redisTemplate.expire(totalKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        String pageTypeKey = RedisKeys.pvDaily(today, pageType);
        redisTemplate.opsForValue().increment(pageTypeKey);
        redisTemplate.expire(pageTypeKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        String hourlyKey = RedisKeys.pvHourly(today, hour);
        redisTemplate.opsForValue().increment(hourlyKey);
        redisTemplate.expire(hourlyKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        if ("PRODUCT_DETAIL".equals(pageType) && StringUtils.hasText(fid)) {
            String productKey = RedisKeys.pvDailyProduct(today, fid);
            redisTemplate.opsForValue().increment(productKey);
            redisTemplate.expire(productKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        }

        if (StringUtils.hasText(userId)) {
            String uvKey = RedisKeys.uvDaily(today);
            redisTemplate.opsForHyperLogLog().add(uvKey, userId);
            redisTemplate.expire(uvKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

            String uvPageTypeKey = RedisKeys.uvDailyPageType(today, pageType);
            redisTemplate.opsForHyperLogLog().add(uvPageTypeKey, userId);
            redisTemplate.expire(uvPageTypeKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

            if ("PRODUCT_DETAIL".equals(pageType) && StringUtils.hasText(fid)) {
                String uvProductKey = RedisKeys.uvDailyProduct(today, fid);
                redisTemplate.opsForHyperLogLog().add(uvProductKey, userId);
                redisTemplate.expire(uvProductKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
            }
        }

        if (StringUtils.hasText(sessionId)) {
            String sessionKey = RedisKeys.sessionDaily(today);
            redisTemplate.opsForHyperLogLog().add(sessionKey, sessionId);
            redisTemplate.expire(sessionKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        }

        if (device != null && StringUtils.hasText(device.getDeviceType())) {
            String deviceType = device.getDeviceType().toLowerCase();
            String deviceKey = RedisKeys.deviceDaily(today, deviceType);
            redisTemplate.opsForValue().increment(deviceKey);
            redisTemplate.expire(deviceKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    private String userIdFromToken(String auth) {
        if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
            return null;
        }
        try {
            String payload = auth.substring(7).split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);
            return node.has("sub") ? node.get("sub").asText() : null;
        } catch (Exception e) {
            log.warn("从JWT解析userId失败: {}", e.getMessage());
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
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