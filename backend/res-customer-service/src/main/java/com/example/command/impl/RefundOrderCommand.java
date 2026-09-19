package com.example.command.impl;

import com.example.api.OrderApi;
import com.example.command.Command;
import com.example.command.CommandContext;
import com.example.command.CommandParam;
import com.example.command.CommandResult;
import com.example.service.LogisticsSimulator;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RefundOrderCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Autowired
    private LogisticsSimulator logisticsSimulator;

    @Override
    public String getName() {
        return "refundOrder";
    }

    @Override
    public String getDisplayName() {
        return "退单";
    }

    @Override
    public String getDescription() {
        return "对已支付或已完成的订单进行退单操作（已送达订单需在2小时内）";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("roid")
                        .displayName("订单号")
                        .type("string")
                        .required(true)
                        .description("要退单的订单编号")
                        .build()
        );
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String roid = (String) context.getParams().get("roid");
        if (roid == null) {
            roid = (String) context.getParams().get("orderId");
        }
        if (roid == null || roid.isEmpty()) {
            return CommandResult.fail("订单号不能为空");
        }
        try {
            ResultVo detailResult = orderApi.orderDetail(roid);
            if (detailResult.getCode() != 200) {
                return CommandResult.fail("订单不存在");
            }

            Map<String, Object> orderData = (Map<String, Object>) detailResult.getData();
            Object statusObj = orderData.get("status");
            int orderStatus = statusObj instanceof Number
                    ? ((Number) statusObj).intValue() : -1;

            if (orderStatus == 0) {
                return CommandResult.fail("订单尚未支付，无法退单。请使用「取消订单」操作");
            }
            if (orderStatus == 3) {
                return CommandResult.fail("订单已取消，无法退单");
            }
            if (orderStatus == 4) {
                return CommandResult.fail("订单已退单，请勿重复操作");
            }
            if (orderStatus != 1 && orderStatus != 2) {
                return CommandResult.fail("当前订单状态不支持退单");
            }

            if (logisticsSimulator.hasLogistics(roid)) {
                LogisticsSimulator.LogisticsInfo info = logisticsSimulator.getLogistics(roid);
                if (info.getStatus() == LogisticsSimulator.DeliveryStatus.DELIVERED) {
                    return CommandResult.fail("订单已送达，如需退单请在收货后 2 小时内申请，或联系人工客服处理");
                }
            }

            Map<String, Object> approvalData = new HashMap<>();
            approvalData.put("approvalId", "APR" + System.currentTimeMillis());
            return CommandResult.builder()
                    .success(true)
                    .needApproval(true)
                    .message("已生成退单审批单，预计24小时内审核")
                    .data(approvalData)
                    .displayType("TEXT")
                    .build();
        } catch (Exception e) {
            log.error("退单失败: roid={}", roid, e);
            String msg = e.getMessage();
            if (msg != null && msg.contains("只有已支付或已完成的订单才能退单")) {
                msg = "当前订单状态不支持退单，仅已支付和已完成的订单可退单";
            }
            return CommandResult.fail("退单失败: " + msg);
        }
    }
}