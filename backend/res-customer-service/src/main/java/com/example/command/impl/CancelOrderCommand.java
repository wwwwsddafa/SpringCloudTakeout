package com.example.command.impl;

import com.example.api.OrderApi;
import com.example.command.Command;
import com.example.command.CommandContext;
import com.example.command.CommandParam;
import com.example.command.CommandResult;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CancelOrderCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Override
    public String getName() {
        return "cancelOrder";
    }

    @Override
    public String getDisplayName() {
        return "取消订单";
    }

    @Override
    public String getDescription() {
        return "取消待支付的订单";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("roid")
                        .displayName("订单号")
                        .type("string")
                        .required(true)
                        .description("要取消的订单编号")
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
            ResultVo result = orderApi.cancelOrder(roid);
            if (result.getCode() == 200) {
                return CommandResult.ok("订单已取消");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("取消订单失败: roid={}", roid, e);
            return CommandResult.fail("取消订单失败: " + e.getMessage());
        }
    }
}