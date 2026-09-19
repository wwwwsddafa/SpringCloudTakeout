package com.example.web.controller;

import com.example.bean.ResOrder;
import com.example.dao.mapper.ResOrderMapper;
import com.example.exceptions.BizException;
import com.example.sevice.OrderService;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/payment")
@Slf4j
@RefreshScope
public class TestPaymentController {

    private static final String TEST_USER_PREFIX = "TEST_";

    @Autowired
    private OrderService orderService;

    @Autowired
    private ResOrderMapper orderMapper;

    @Value("${test.payment.enabled:false}")
    private boolean testPaymentEnabled;

    @PostMapping("/pay/{roid}")
    public ResultVo testPay(@RequestHeader("X-User-Id") String userId,
                            @PathVariable String roid) {
        if (!testPaymentEnabled) {
            return ResultVo.fail(403, "测试支付通道未开启，请联系管理员配置 test.payment.enabled=true");
        }

        if (userId == null || !userId.startsWith(TEST_USER_PREFIX)) {
            log.warn("非测试用户尝试使用测试支付通道: userId={}, roid={}", userId, roid);
            return ResultVo.fail(403, "仅测试用户（TEST_ 前缀）可使用此支付通道");
        }

        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            return ResultVo.fail(404, "订单不存在: " + roid);
        }
        if (!userId.equals(order.getUserid())) {
            log.warn("测试用户尝试支付他人订单: userId={}, orderUserId={}, roid={}",
                    userId, order.getUserid(), roid);
            return ResultVo.fail(403, "只能支付自己的订单");
        }
        if (order.getStatus() != 0) {
            return ResultVo.fail(400, "订单状态不允许支付，当前状态: " + order.getStatus());
        }

        try {
            orderService.confirmOrder(roid);
            log.info("测试支付成功: userId={}, roid={}, amount={}", userId, roid, order.getPayAmount());
            return ResultVo.success("测试支付成功");
        } catch (BizException e) {
            log.error("测试支付失败: userId={}, roid={}, error={}", userId, roid, e.getMessage());
            return ResultVo.fail(e.getCode(), e.getMessage());
        }
    }
}