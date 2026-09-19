package com.example.sevice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.IdGeneratorApi;
import com.example.bean.ResFreeOrderEvent;
import com.example.bean.ResFreeOrderRecord;
import com.example.dao.mapper.ResFreeOrderEventMapper;
import com.example.dao.mapper.ResFreeOrderRecordMapper;
import com.example.exceptions.BizException;
import com.example.sevice.FreeOrderService;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FreeOrderServiceImpl implements FreeOrderService {

    private static final String FREE_ORDER_GRAB_PREFIX = "free_order:grab:";
    private static final String FREE_ORDER_REMAIN_KEY = "free_order:remain:";

    private static final String GRAB_LUA_SCRIPT =
            "local remain = tonumber(redis.call('GET', KEYS[1])) " +
            "if remain == nil or remain <= 0 then " +
            "  return -1 " +
            "end " +
            "redis.call('DECR', KEYS[1]) " +
            "return remain - 1";

    @Autowired
    private ResFreeOrderEventMapper eventMapper;

    @Autowired
    private ResFreeOrderRecordMapper recordMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IdGeneratorApi idGeneratorApi;

    @Override
    @Transactional
    public ResFreeOrderEvent createEvent(ResFreeOrderEvent event, String adminId) {
        if (event.getTotalCount() == null || event.getTotalCount() <= 0) {
            throw new BizException(ResultCode.FREE_ORDER_COUNT_INVALID);
        }
        if (event.getMaxAmount() == null || event.getMaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.FREE_ORDER_MAX_AMOUNT_INVALID);
        }
        if (event.getMinAmount() == null || event.getMinAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.FREE_ORDER_MIN_AMOUNT_INVALID);
        }
        if (event.getMinAmount().compareTo(event.getMaxAmount()) > 0) {
            throw new BizException(ResultCode.FREE_ORDER_MIN_OVER_MAX);
        }
        if (event.getStartTime() == null || event.getEndTime() == null) {
            throw new BizException(ResultCode.FREE_ORDER_TIME_INVALID);
        }
        if (event.getStartTime().isAfter(event.getEndTime())) {
            throw new BizException(ResultCode.FREE_ORDER_TIME_ORDER_INVALID);
        }

        String eventId = generateId();
        event.setEventId(eventId);
        event.setRemainCount(event.getTotalCount());
        event.setStatus(0);
        event.setCreateTime(LocalDateTime.now());
        event.setAdminId(adminId);

        eventMapper.insert(event);

        redisTemplate.opsForValue().set(FREE_ORDER_REMAIN_KEY + eventId,
                String.valueOf(event.getTotalCount()));
        redisTemplate.expire(FREE_ORDER_REMAIN_KEY + eventId, 24, TimeUnit.HOURS);

        log.info("免单活动创建成功: eventId={}, totalCount={}, minAmount={}, maxAmount={}",
                eventId, event.getTotalCount(), event.getMinAmount(), event.getMaxAmount());
        return event;
    }

    @Override
    @Transactional
    public ResFreeOrderEvent updateEvent(String eventId, ResFreeOrderEvent update, String adminId) {
        ResFreeOrderEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(event.getStartTime())) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_EDITABLE);
        }

        if (update.getEventName() != null) {
            event.setEventName(update.getEventName());
        }
        if (update.getMinAmount() != null) {
            if (update.getMinAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.FREE_ORDER_MIN_AMOUNT_INVALID);
            }
            event.setMinAmount(update.getMinAmount());
        }
        if (update.getMaxAmount() != null) {
            if (update.getMaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.FREE_ORDER_MAX_AMOUNT_INVALID);
            }
            event.setMaxAmount(update.getMaxAmount());
        }
        if (event.getMinAmount().compareTo(event.getMaxAmount()) > 0) {
            throw new BizException(ResultCode.FREE_ORDER_MIN_OVER_MAX);
        }
        if (update.getStartTime() != null) {
            if (update.getStartTime().isAfter(event.getEndTime())) {
                throw new BizException(ResultCode.FREE_ORDER_TIME_ORDER_INVALID);
            }
            event.setStartTime(update.getStartTime());
        }
        if (update.getEndTime() != null) {
            if (event.getStartTime().isAfter(update.getEndTime())) {
                throw new BizException(ResultCode.FREE_ORDER_END_TIME_INVALID);
            }
            event.setEndTime(update.getEndTime());
        }
        if (update.getTotalCount() != null) {
            if (update.getTotalCount() <= 0) {
                throw new BizException(ResultCode.FREE_ORDER_COUNT_INVALID);
            }
            event.setTotalCount(update.getTotalCount());
            event.setRemainCount(update.getTotalCount());
            redisTemplate.opsForValue().set(FREE_ORDER_REMAIN_KEY + eventId,
                    String.valueOf(update.getTotalCount()));
            redisTemplate.expire(FREE_ORDER_REMAIN_KEY + eventId, 24, TimeUnit.HOURS);
        }

        eventMapper.updateById(event);
        log.info("免单活动编辑成功: eventId={}", eventId);
        return event;
    }

    @Override
    @Transactional
    public void deleteEvent(String eventId) {
        ResFreeOrderEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(event.getStartTime())) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_DELETABLE);
        }

        eventMapper.deleteById(eventId);
        redisTemplate.delete(FREE_ORDER_REMAIN_KEY + eventId);
        log.info("免单活动删除成功: eventId={}", eventId);
    }

    @Override
    public Map<String, Object> getEventDetail(String eventId) {
        ResFreeOrderEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> detail = new HashMap<>();
        detail.put("eventId", event.getEventId());
        detail.put("eventName", event.getEventName());
        detail.put("totalCount", event.getTotalCount());
        detail.put("minAmount", event.getMinAmount());
        detail.put("maxAmount", event.getMaxAmount());
        detail.put("startTime", event.getStartTime().toString());
        detail.put("endTime", event.getEndTime().toString());
        detail.put("status", resolveStatus(event.getStatus(), event.getStartTime(), event.getEndTime(), now));
        detail.put("createTime", event.getCreateTime().toString());
        detail.put("adminId", event.getAdminId());

        Long grabbedCount = recordMapper.selectCount(
                new LambdaQueryWrapper<ResFreeOrderRecord>()
                        .eq(ResFreeOrderRecord::getEventId, eventId));
        detail.put("grabbedCount", grabbedCount);

        if (now.isBefore(event.getStartTime())) {
            detail.put("remainCount", event.getRemainCount());
        } else if (now.isAfter(event.getEndTime()) || (event.getStatus() != null && event.getStatus() == 1)) {
            detail.put("remainCount", 0);
        } else {
            String remainStr = redisTemplate.opsForValue().get(FREE_ORDER_REMAIN_KEY + eventId);
            int remain = remainStr != null ? Integer.parseInt(remainStr) : event.getRemainCount();
            detail.put("remainCount", remain);
        }

        return detail;
    }

    @Override
    @Transactional
    public void endEvent(String eventId) {
        ResFreeOrderEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_FOUND);
        }

        if (event.getStatus() != null && event.getStatus() == 1) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_ENDED);
        }

        event.setStatus(1);
        eventMapper.updateById(event);
        redisTemplate.delete(FREE_ORDER_REMAIN_KEY + eventId);
        log.info("免单活动提前结束: eventId={}", eventId);
    }

    @Override
    public List<Map<String, Object>> getCurrentEvents() {
        LocalDateTime now = LocalDateTime.now();

        List<ResFreeOrderEvent> activeEvents = eventMapper.selectList(
                new LambdaQueryWrapper<ResFreeOrderEvent>()
                        .le(ResFreeOrderEvent::getStartTime, now)
                        .ge(ResFreeOrderEvent::getEndTime, now)
                        .eq(ResFreeOrderEvent::getStatus, 0)
                        .orderByDesc(ResFreeOrderEvent::getCreateTime));

        List<ResFreeOrderEvent> pendingEvents = eventMapper.selectList(
                new LambdaQueryWrapper<ResFreeOrderEvent>()
                        .gt(ResFreeOrderEvent::getStartTime, now)
                        .eq(ResFreeOrderEvent::getStatus, 0)
                        .orderByAsc(ResFreeOrderEvent::getStartTime));

        List<Map<String, Object>> resultList = new ArrayList<>();

        for (ResFreeOrderEvent event : activeEvents) {
            Map<String, Object> item = buildEventResult(event, now, "active");
            resultList.add(item);
        }

        for (ResFreeOrderEvent event : pendingEvents) {
            Map<String, Object> item = buildEventResult(event, now, "pending");
            resultList.add(item);
        }

        return resultList;
    }

    private Map<String, Object> buildEventResult(ResFreeOrderEvent event, LocalDateTime now, String status) {
        Map<String, Object> result = new HashMap<>();
        result.put("hasEvent", true);
        result.put("eventId", event.getEventId());
        result.put("eventName", event.getEventName());
        result.put("minAmount", event.getMinAmount());
        result.put("maxAmount", event.getMaxAmount());
        result.put("startTime", event.getStartTime().toString());
        result.put("endTime", event.getEndTime().toString());
        result.put("serverTime", now.toString());
        result.put("status", status);

        if ("pending".equals(status)) {
            result.put("remainCount", event.getRemainCount());
        } else if ("active".equals(status)) {
            String remainStr = redisTemplate.opsForValue().get(FREE_ORDER_REMAIN_KEY + event.getEventId());
            int remain = remainStr != null ? Integer.parseInt(remainStr) : event.getRemainCount();
            result.put("remainCount", remain);
        } else {
            result.put("remainCount", 0);
        }

        return result;
    }

    @Override
    public ResultVo grab(String userId) {
        LocalDateTime now = LocalDateTime.now();

        ResFreeOrderEvent event = eventMapper.selectOne(
                new LambdaQueryWrapper<ResFreeOrderEvent>()
                        .le(ResFreeOrderEvent::getStartTime, now)
                        .ge(ResFreeOrderEvent::getEndTime, now)
                        .eq(ResFreeOrderEvent::getStatus, 0)
                        .orderByDesc(ResFreeOrderEvent::getCreateTime)
                        .last("LIMIT 1"));

        if (event == null) {
            throw new BizException(ResultCode.FREE_ORDER_NO_EVENT);
        }

        String grabKey = FREE_ORDER_GRAB_PREFIX + event.getEventId() + ":" + userId;
        Boolean grabbed = redisTemplate.opsForValue()
                .setIfAbsent(grabKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(grabbed)) {
            log.warn("重复抢单: grabKey={} 已存在", grabKey);
            throw new BizException(ResultCode.FREE_ORDER_ALREADY_GRABBED);
        }

        String remainKey = FREE_ORDER_REMAIN_KEY + event.getEventId();
        DefaultRedisScript<Long> luaScript = new DefaultRedisScript<>(GRAB_LUA_SCRIPT, Long.class);
        Long remainAfter;
        try {
            remainAfter = redisTemplate.execute(luaScript,
                    Collections.singletonList(remainKey));
        } catch (Exception e) {
            redisTemplate.delete(grabKey);
            log.error("Lua脚本执行失败: eventId={}", event.getEventId(), e);
            throw new BizException(ResultCode.SYSTEM_BUSY);
        }

        if (remainAfter == null || remainAfter < 0) {
            redisTemplate.delete(grabKey);
            throw new BizException(ResultCode.FREE_ORDER_SOLD_OUT);
        }

        BigDecimal couponAmount = generateRandomAmount(event.getMinAmount(), event.getMaxAmount());

        String freeOrderNo = generateId();
        ResFreeOrderRecord record = new ResFreeOrderRecord();
        record.setRecordId(UUID.randomUUID().toString().replace("-", ""));
        record.setEventId(event.getEventId());
        record.setUserId(userId);
        record.setGrabTime(now);
        record.setFreeOrderNo(freeOrderNo);
        record.setCouponAmount(couponAmount);
        record.setUsed(0);

        try {
            recordMapper.insert(record);
        } catch (Exception e) {
            redisTemplate.opsForValue().increment(remainKey);
            redisTemplate.delete(grabKey);
            log.error("抢券记录写入失败，已回滚Redis: eventId={}, userId={}",
                    event.getEventId(), userId, e);
            throw new BizException(ResultCode.SYSTEM_BUSY);
        }

        if (remainAfter == 0) {
            try {
                ResFreeOrderEvent updateEvent = new ResFreeOrderEvent();
                updateEvent.setEventId(event.getEventId());
                updateEvent.setStatus(1);
                eventMapper.updateById(updateEvent);
            } catch (Exception e) {
                log.warn("活动状态更新失败(非致命): eventId={}", event.getEventId(), e);
            }
        }

        log.info("用户抢到免单: userId={}, eventId={}, freeOrderNo={}, couponAmount={}",
                userId, event.getEventId(), freeOrderNo, couponAmount);

        Map<String, Object> data = new HashMap<>();
        data.put("freeOrderNo", freeOrderNo);
        data.put("couponAmount", couponAmount);
        data.put("minAmount", event.getMinAmount());
        data.put("maxAmount", event.getMaxAmount());
        data.put("eventName", event.getEventName());
        return ResultVo.success(data);
    }

    private BigDecimal generateRandomAmount(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount.compareTo(maxAmount) == 0) {
            return minAmount;
        }
        double randomValue = Math.random();
        BigDecimal range = maxAmount.subtract(minAmount);
        BigDecimal result = minAmount.add(range.multiply(BigDecimal.valueOf(randomValue)));
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<Map<String, Object>> myCoupons(String userId) {
        List<ResFreeOrderRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<ResFreeOrderRecord>()
                        .eq(ResFreeOrderRecord::getUserId, userId)
                        .eq(ResFreeOrderRecord::getUsed, 0)
                        .orderByDesc(ResFreeOrderRecord::getGrabTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ResFreeOrderRecord record : records) {
            ResFreeOrderEvent event = eventMapper.selectById(record.getEventId());
            Map<String, Object> item = new HashMap<>();
            item.put("couponNo", record.getFreeOrderNo());
            item.put("eventName", event != null ? event.getEventName() : "未知活动");
            item.put("couponAmount", record.getCouponAmount());
            item.put("minAmount", event != null ? event.getMinAmount() : BigDecimal.ZERO);
            item.put("maxAmount", event != null ? event.getMaxAmount() : BigDecimal.ZERO);
            item.put("createTime", record.getGrabTime().toString());
            item.put("status", record.getUsed() == 1 ? "USED" : "UNUSED");
            result.add(item);
        }
        return result;
    }

    @Override
    public Page<Map<String, Object>> listEvents(Integer page, Integer size) {
        Page<ResFreeOrderEvent> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ResFreeOrderEvent> wrapper = new LambdaQueryWrapper<ResFreeOrderEvent>()
                .orderByDesc(ResFreeOrderEvent::getCreateTime);
        Page<ResFreeOrderEvent> eventPage = eventMapper.selectPage(pageParam, wrapper);

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> records = eventPage.getRecords().stream().map(event -> {
            Map<String, Object> item = new HashMap<>();
            item.put("eventId", event.getEventId());
            item.put("eventName", event.getEventName());
            item.put("totalCount", event.getTotalCount());
            item.put("remainCount", event.getRemainCount());
            item.put("minAmount", event.getMinAmount());
            item.put("maxAmount", event.getMaxAmount());
            item.put("startTime", event.getStartTime().toString());
            item.put("endTime", event.getEndTime().toString());
            item.put("status", resolveStatus(event.getStatus(), event.getStartTime(), event.getEndTime(), now));
            item.put("createTime", event.getCreateTime().toString());
            item.put("adminId", event.getAdminId());

            Long grabbedCount = recordMapper.selectCount(
                    new LambdaQueryWrapper<ResFreeOrderRecord>()
                            .eq(ResFreeOrderRecord::getEventId, event.getEventId()));
            item.put("grabbedCount", grabbedCount);
            return item;
        }).collect(Collectors.toList());

        Page<Map<String, Object>> resultPage = new Page<>();
        resultPage.setTotal(eventPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    private String resolveStatus(Integer status, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (status != null && status == 1) {
            return "ended";
        }
        if (now.isBefore(startTime)) {
            return "pending";
        } else if (now.isAfter(endTime)) {
            return "ended";
        } else {
            return "active";
        }
    }

    @Override
    @Transactional
    public BigDecimal useCoupon(String freeOrderNo, String userId, String orderId, BigDecimal orderAmount) {
        BigDecimal actualAmount = validateCoupon(freeOrderNo, userId, orderAmount);

        ResFreeOrderRecord record = recordMapper.selectOne(
                new LambdaQueryWrapper<ResFreeOrderRecord>()
                        .eq(ResFreeOrderRecord::getFreeOrderNo, freeOrderNo)
                        .eq(ResFreeOrderRecord::getUserId, userId)
                        .eq(ResFreeOrderRecord::getUsed, 0));

        record.setUsed(1);
        record.setUsedOrderId(orderId);
        record.setUsedAmount(actualAmount);
        recordMapper.updateById(record);

        log.info("免单券核销成功: freeOrderNo={}, orderId={}, usedAmount={}",
                freeOrderNo, orderId, actualAmount);
        return actualAmount;
    }

    @Override
    public BigDecimal validateCoupon(String freeOrderNo, String userId, BigDecimal orderAmount) {
        if (freeOrderNo == null || freeOrderNo.isEmpty()) {
            throw new BizException(ResultCode.FREE_ORDER_COUPON_NO_EMPTY);
        }

        ResFreeOrderRecord record = recordMapper.selectOne(
                new LambdaQueryWrapper<ResFreeOrderRecord>()
                        .eq(ResFreeOrderRecord::getFreeOrderNo, freeOrderNo));

        if (record == null) {
            throw new BizException(ResultCode.FREE_ORDER_COUPON_NOT_FOUND);
        }

        if (!record.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FREE_ORDER_COUPON_NOT_OWNER);
        }

        if (record.getUsed() == 1) {
            throw new BizException(ResultCode.FREE_ORDER_COUPON_USED);
        }

        ResFreeOrderEvent event = eventMapper.selectById(record.getEventId());
        if (event == null) {
            throw new BizException(ResultCode.FREE_ORDER_EVENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(event.getEndTime())) {
            throw new BizException(ResultCode.FREE_ORDER_COUPON_EXPIRED);
        }

        BigDecimal couponAmount = record.getCouponAmount();
        BigDecimal actualAmount = orderAmount;
        if (orderAmount.compareTo(couponAmount) > 0) {
            actualAmount = couponAmount;
        }

        log.info("免单券校验通过: freeOrderNo={}, userId={}, 券面值={}, 减免金额={}",
                freeOrderNo, userId, couponAmount, actualAmount);
        return actualAmount;
    }

    private String generateId() {
        try {
            ResultVo result = idGeneratorApi.next();
            if (result != null && result.getData() != null) {
                return result.getData().toString();
            }
        } catch (Exception e) {
            log.warn("ID生成服务调用失败，使用UUID: {}", e.getMessage());
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}