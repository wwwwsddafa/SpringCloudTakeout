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
public class ModifyAddressCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Override
    public String getName() {
        return "modifyAddress";
    }

    @Override
    public String getDisplayName() {
        return "修改订单地址";
    }

    @Override
    public String getDescription() {
        return "修改指定订单的收货地址";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("roid")
                        .displayName("订单号")
                        .type("string")
                        .required(true)
                        .description("要修改地址的订单编号")
                        .build(),
                CommandParam.builder()
                        .name("address")
                        .displayName("新地址")
                        .type("string")
                        .required(true)
                        .description("新的收货地址")
                        .build()
        );
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String roid = (String) context.getParams().get("roid");
        if (roid == null) {
            roid = (String) context.getParams().get("orderId");
        }
        String address = (String) context.getParams().get("address");
        if (address == null) {
            address = (String) context.getParams().get("newAddress");
        }
        if (roid == null || roid.isEmpty()) {
            return CommandResult.fail("订单号不能为空");
        }
        if (address == null || address.isEmpty()) {
            return CommandResult.fail("新地址不能为空");
        }
        try {
            ResultVo result = orderApi.updateAddress(roid, address);
            if (result.getCode() == 200) {
                return CommandResult.ok("地址修改成功");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("修改地址失败: roid={}", roid, e);
            return CommandResult.fail("修改地址失败: " + e.getMessage());
        }
    }
}