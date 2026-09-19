package com.example.web.controller;

import com.example.bean.ResFreeOrderEvent;
import com.example.sevice.FreeOrderService;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@Slf4j
public class FreeOrderController {

    @Autowired
    private FreeOrderService freeOrderService;

    @PostMapping("/admin/free-order/event")
    public ResultVo createEvent(@RequestHeader("X-User-Id") String adminId,
                                @RequestBody ResFreeOrderEvent event) {
        ResFreeOrderEvent created = freeOrderService.createEvent(event, adminId);
        return ResultVo.success(created);
    }

    @GetMapping("/admin/free-order/events")
    public ResultVo listEvents(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer size) {
        return ResultVo.success(freeOrderService.listEvents(page, size));
    }

    @PutMapping("/admin/free-order/event/{eventId}")
    public ResultVo updateEvent(@PathVariable String eventId,
                                @RequestHeader("X-User-Id") String adminId,
                                @RequestBody ResFreeOrderEvent event) {
        ResFreeOrderEvent updated = freeOrderService.updateEvent(eventId, event, adminId);
        return ResultVo.success(updated);
    }

    @DeleteMapping("/admin/free-order/event/{eventId}")
    public ResultVo deleteEvent(@PathVariable String eventId) {
        freeOrderService.deleteEvent(eventId);
        return ResultVo.success("删除成功");
    }

    @GetMapping("/admin/free-order/event/{eventId}")
    public ResultVo getEventDetail(@PathVariable String eventId) {
        Map<String, Object> detail = freeOrderService.getEventDetail(eventId);
        return ResultVo.success(detail);
    }

    @PostMapping("/admin/free-order/event/{eventId}/end")
    public ResultVo endEvent(@PathVariable String eventId) {
        freeOrderService.endEvent(eventId);
        return ResultVo.success("活动已结束");
    }

    @GetMapping("/free-order/event/current")
    public ResultVo getCurrentEvents() {
        List<Map<String, Object>> events = freeOrderService.getCurrentEvents();
        return ResultVo.success(events);
    }

    @PostMapping("/free-order/grab")
    public ResultVo grab(@RequestHeader("X-User-Id") String userId) {
        return freeOrderService.grab(userId);
    }

    @GetMapping("/free-order/my-coupons")
    public ResultVo myCoupons(@RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> coupons = freeOrderService.myCoupons(userId);
        return ResultVo.success(coupons);
    }

    @GetMapping("/free-order/usable-coupons")
    public ResultVo usableCoupons(@RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> coupons = freeOrderService.myCoupons(userId);
        return ResultVo.success(coupons);
    }
}