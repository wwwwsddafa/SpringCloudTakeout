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

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class QueryOrderLogisticsCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Autowired
    private LogisticsSimulator logisticsSimulator;

    @Override
    public String getName() {
        return "queryOrderLogistics";
    }

    @Override
    public String getDisplayName() {
        return "查询配送状态";
    }

    @Override
    public String getDescription() {
        return "根据订单号查询外卖配送进度";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("roid")
                        .displayName("订单号")
                        .type("string")
                        .required(true)
                        .description("要查询配送进度的订单编号")
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
            if (result.getCode() != 200) {
                return CommandResult.fail(result.getMsg());
            }

            Map<String, Object> logistics = logisticsSimulator.buildLogisticsResponse(roid);
            return CommandResult.ok("配送状态查询成功", logistics, "CARD");
        } catch (Exception e) {
            log.error("查询配送状态失败: roid={}", roid, e);
            return CommandResult.fail("查询配送状态失败: " + e.getMessage());
        }
    }
}