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
public class QueryOrderCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Override
    public String getName() {
        return "queryOrder";
    }

    @Override
    public String getDisplayName() {
        return "查询订单";
    }

    @Override
    public String getDescription() {
        return "根据订单号查询订单详情";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("roid")
                        .displayName("订单号")
                        .type("string")
                        .required(true)
                        .description("要查询的订单编号")
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
            ResultVo result = orderApi.orderDetail(roid);
            if (result.getCode() == 200) {
                return CommandResult.ok("查询成功", result.getData(), "CARD");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("查询订单失败: roid={}", roid, e);
            return CommandResult.fail("查询订单失败: " + e.getMessage());
        }
    }
}