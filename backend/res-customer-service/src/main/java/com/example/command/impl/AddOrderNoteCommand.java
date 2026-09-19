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
public class AddOrderNoteCommand implements Command {

    @Autowired
    private OrderApi orderApi;

    @Override
    public String getName() {
        return "addOrderNote";
    }

    @Override
    public String getDisplayName() {
        return "订单备注";
    }

    @Override
    public String getDescription() {
        return "为订单添加客服备注信息";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("roid")
                        .displayName("订单号")
                        .type("string")
                        .required(true)
                        .description("要添加备注的订单编号")
                        .build(),
                CommandParam.builder()
                        .name("note")
                        .displayName("备注内容")
                        .type("string")
                        .required(true)
                        .description("备注文本内容")
                        .build()
        );
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String roid = (String) context.getParams().get("roid");
        if (roid == null) {
            roid = (String) context.getParams().get("orderId");
        }
        String note = (String) context.getParams().get("note");
        if (roid == null || roid.isEmpty()) {
            return CommandResult.fail("订单号不能为空");
        }
        if (note == null || note.isEmpty()) {
            return CommandResult.fail("备注内容不能为空");
        }
        try {
            ResultVo result = orderApi.orderDetail(roid);
            if (result.getCode() == 200) {
                return CommandResult.ok("备注已添加: " + roid);
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("添加订单备注失败: roid={}", roid, e);
            return CommandResult.fail("添加备注失败: " + e.getMessage());
        }
    }
}