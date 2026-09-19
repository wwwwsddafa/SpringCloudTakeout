package com.example.command.impl;

import com.example.api.UserServiceApi;
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
public class QueryUserInfoCommand implements Command {

    @Autowired
    private UserServiceApi userServiceApi;

    @Override
    public String getName() {
        return "queryUserInfo";
    }

    @Override
    public String getDisplayName() {
        return "查询用户信息";
    }

    @Override
    public String getDescription() {
        return "查询当前用户的基本信息";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of();
    }

    @Override
    public CommandResult execute(CommandContext context) {
        try {
            ResultVo result = userServiceApi.getUserInfoById(context.getUserId());
            if (result.getCode() == 200) {
                return CommandResult.ok("查询成功", result.getData(), "CARD");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("查询用户信息失败: userId={}", context.getUserId(), e);
            return CommandResult.fail("查询用户信息失败: " + e.getMessage());
        }
    }
}