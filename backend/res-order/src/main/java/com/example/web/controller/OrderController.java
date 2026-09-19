package com.example.web.controller;

import com.example.sevice.OrderService;
import com.example.web.vo.CreateOrderVo;
import com.example.web.vo.OrderVo;
import com.example.web.vo.ResultVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public ResultVo createOrder(@RequestHeader("X-User-Id") String userId,
                                @RequestBody CreateOrderVo createOrderVo) {
        OrderVo order = orderService.createOrder(userId, createOrderVo);
        return ResultVo.success(order);
    }

    @GetMapping("/admin/list")
    public ResultVo adminListOrders() {
        List<OrderVo> orders = orderService.adminListOrders();
        return ResultVo.success(orders);
    }

    @GetMapping("/list")
    public ResultVo listOrders(@RequestHeader("X-User-Id") String userId) {
        List<OrderVo> orders = orderService.listOrders(userId);
        return ResultVo.success(orders);
    }

    @GetMapping("/detail/{roid}")
    public ResultVo orderDetail(@PathVariable String roid) {
        OrderVo order = orderService.orderDetail(roid);
        return ResultVo.success(order);
    }

    @PostMapping("/confirm/{roid}")
    public ResultVo confirmOrder(@PathVariable String roid) {
        orderService.confirmOrder(roid);
        return ResultVo.success("支付成功");
    }

    @PostMapping("/cancel/{roid}")
    public ResultVo cancelOrder(@PathVariable String roid) {
        orderService.cancelOrder(roid);
        return ResultVo.success("已取消");
    }

    @GetMapping("/checkPurchase")
    public ResultVo checkPurchase(@RequestHeader("X-User-Id") String userId,
                                  @RequestParam("fid") String fid) {
        boolean purchased = orderService.checkPurchase(userId, fid);
        return ResultVo.success(purchased);
    }

    @PutMapping("/address/{roid}")
    public ResultVo updateAddress(@PathVariable String roid,
                                  @RequestParam String address) {
        orderService.updateAddress(roid, address);
        return ResultVo.success("地址修改成功");
    }

    @PostMapping("/refund/{roid}")
    public ResultVo refundOrder(@PathVariable String roid) {
        orderService.refundOrder(roid);
        return ResultVo.success("退单成功");
    }

    @PostMapping("/admin/updateStatus/{roid}")
    public ResultVo adminUpdateStatus(@PathVariable String roid,
                                      @RequestParam(required = false) Integer status,
                                      @RequestBody(required = false) Map<String, Object> request) {
        if (status == null && request != null) {
            status = request.get("status") instanceof Number
                    ? ((Number) request.get("status")).intValue() : null;
        }
        if (status == null) {
            return ResultVo.fail(400, "状态值不能为空，请传入 status 参数");
        }
        orderService.updateStatus(roid, status);
        return ResultVo.success("状态更新成功");
    }

    @PostMapping("/alipay/pay/{roid}")
    public ResultVo alipayPay(@PathVariable String roid) {
        String form = orderService.createAlipayPayForm(roid);
        return ResultVo.success(form);
    }

    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        log.info("===== 支付宝异步通知到达 =====");
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }
        log.info("支付宝异步通知 params: {}", params);
        try {
            orderService.handleAlipayNotify(params);
            log.info("===== 支付宝异步通知处理成功 =====");
            return "success";
        } catch (Exception e) {
            log.error("===== 支付宝异步通知处理失败 =====", e);
            return "fail";
        }
    }

    @GetMapping("/alipay/return")
    public ResultVo alipayReturn(HttpServletRequest request) {
        log.info("===== 支付宝同步跳转到达 =====");
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }
        log.info("支付宝同步返回: params={}", params);
        return ResultVo.success(params);
    }
}