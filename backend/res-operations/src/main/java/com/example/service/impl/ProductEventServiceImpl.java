package com.example.service.impl;

import com.example.api.ProductApi;
import com.example.constants.RedisKeys;
import com.example.service.ProductEventService;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ProductEventServiceImpl implements ProductEventService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductApi productApi;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int REDIS_TTL_HOURS = 48;

    @Override
    public ResultVo trackPv(String deviceType) {
        String today = LocalDate.now().format(DATE_FMT);
        String deviceKey = RedisKeys.deviceDaily(today, deviceType != null ? deviceType : "unknown");
        redisTemplate.opsForValue().increment(deviceKey);
        redisTemplate.expire(deviceKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        log.info("PV事件: deviceType={}", deviceType);
        return ResultVo.success("ok");
    }

    @Override
    public ResultVo trackCartAdd(String fid) {
        String today = LocalDate.now().format(DATE_FMT);
        String cartKey = RedisKeys.cartDailyProduct(today, fid);
        redisTemplate.opsForValue().increment(cartKey);
        redisTemplate.expire(cartKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        log.info("购物车事件: fid={}", fid);
        return ResultVo.success("ok");
    }

    @Override
    public ResultVo trackPaidOrder(String fid, int count) {
        String today = LocalDate.now().format(DATE_FMT);
        String paidKey = RedisKeys.orderDailyPaidProduct(today, fid);
        redisTemplate.opsForValue().increment(paidKey, count);
        redisTemplate.expire(paidKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        BigDecimal unit = getRealPrice(fid);
        if (unit != null) {
            BigDecimal amt = unit.multiply(BigDecimal.valueOf(count));
            String amtKey = RedisKeys.orderDailyPaidAmountProduct(today, fid);
            redisTemplate.opsForValue().increment(amtKey, amt.doubleValue());
            redisTemplate.expire(amtKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        }
        log.info("已付款事件: fid={}, count={}", fid, count);
        return ResultVo.success("ok");
    }

    private BigDecimal getRealPrice(String fid) {
        try {
            ResultVo result = productApi.getProductDetail(fid);
            if (result != null && result.getCode() == 200 && result.getData() != null
                    && result.getData() instanceof Map) {
                Object p = ((Map<?, ?>) result.getData()).get("realprice");
                if (p != null) return new BigDecimal(p.toString());
            }
        } catch (Exception e) {
            log.warn("获取菜品单价失败 fid={}: {}", fid, e.getMessage());
        }
        return null;
    }

    @Override
    public ResultVo trackOrderCompleted(BigDecimal amount, String userId) {
        String today = LocalDate.now().format(DATE_FMT);
        String totalKey = RedisKeys.orderDailyTotal(today);
        String paidKey = RedisKeys.orderDailyPaid(today);
        String paidAmountKey = RedisKeys.orderDailyPaidAmount(today);
        String totalAmountKey = RedisKeys.orderDailyAmount(today);

        redisTemplate.opsForValue().increment(totalKey, 1);
        redisTemplate.expire(totalKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().increment(paidKey, 1);
        redisTemplate.expire(paidKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        BigDecimal amt = amount != null ? amount : BigDecimal.ZERO;

        redisTemplate.opsForValue().increment(totalAmountKey, amt.doubleValue());
        redisTemplate.expire(totalAmountKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().increment(paidAmountKey, amt.doubleValue());
        redisTemplate.expire(paidAmountKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        int hour = LocalDateTime.now().getHour();
        String hourlyOrderKey = RedisKeys.orderHourly(today, hour);
        redisTemplate.opsForValue().increment(hourlyOrderKey, 1);
        redisTemplate.expire(hourlyOrderKey, REDIS_TTL_HOURS, TimeUnit.HOURS);

        // ③ 下单用户去重(HLL)
        if (userId != null && !userId.isEmpty()) {
            String uvOrderKey = RedisKeys.uvOrderDaily(today);
            redisTemplate.opsForHyperLogLog().add(uvOrderKey, userId);
            redisTemplate.expire(uvOrderKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        }

        log.info("订单完成事件: total=+1, paid=+1, amount=+{}, hour={}, userId={}", amt, hour, userId);
        return ResultVo.success("ok");
    }

    @Override
    public ResultVo trackUserRegister() {
        String today = LocalDate.now().format(DATE_FMT);
        String newUserKey = RedisKeys.userDailyNew(today);
        redisTemplate.opsForValue().increment(newUserKey);
        redisTemplate.expire(newUserKey, REDIS_TTL_HOURS, TimeUnit.HOURS);
        log.info("新用户注册事件: date={}", today);
        return ResultVo.success("ok");
    }
}