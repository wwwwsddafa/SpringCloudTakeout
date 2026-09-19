package com.example.sevice;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bean.ResFreeOrderEvent;
import com.example.web.vo.ResultVo;

import java.util.List;
import java.util.Map;

public interface FreeOrderService {

    ResFreeOrderEvent createEvent(ResFreeOrderEvent event, String adminId);

    ResFreeOrderEvent updateEvent(String eventId, ResFreeOrderEvent event, String adminId);

    void deleteEvent(String eventId);

    Map<String, Object> getEventDetail(String eventId);

    void endEvent(String eventId);

    List<Map<String, Object>> getCurrentEvents();

    ResultVo grab(String userId);

    List<Map<String, Object>> myCoupons(String userId);

    java.math.BigDecimal useCoupon(String freeOrderNo, String userId, String orderId, java.math.BigDecimal orderAmount);

    java.math.BigDecimal validateCoupon(String freeOrderNo, String userId, java.math.BigDecimal orderAmount);

    Page<Map<String, Object>> listEvents(Integer page, Integer size);
}