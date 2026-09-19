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
public class ListUserOrdersCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Override
    public String getName() {
        return "listUserOrders";
    }

    @Override
    public String getDisplayName() {
        return "用户订单列表";
    }

    @Override
    public String getDescription() {
        return "查询当前用户的所有订单";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of();
    }

    @Override
    public CommandResult execute(CommandContext context) {
        try {
            ResultVo result = orderApi.listOrders(context.getUserId());
            if (result.getCode() == 200) {
                return CommandResult.ok("查询成功", result.getData(), "TABLE");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("查询用户订单列表失败: userId={}", context.getUserId(), e);
            return CommandResult.fail("查询订单列表失败: " + e.getMessage());
        }
    }
}