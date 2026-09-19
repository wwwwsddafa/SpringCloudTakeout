package com.example.command.impl;

import com.example.api.ProductApi;
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
public class QueryProductCommand implements Command {

    @Autowired
    private ProductApi productApi;

    @Override
    public String getName() {
        return "queryProduct";
    }

    @Override
    public String getDisplayName() {
        return "查询商品";
    }

    @Override
    public String getDescription() {
        return "根据商品ID查询商品详情";
    }

    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("fid")
                        .displayName("商品ID")
                        .type("string")
                        .required(true)
                        .description("要查询的商品编号")
                        .build()
        );
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String fid = (String) context.getParams().get("fid");
        if (fid == null || fid.isEmpty()) {
            return CommandResult.fail("商品ID不能为空");
        }
        try {
            ResultVo result = productApi.getProductDetail(fid);
            if (result.getCode() == 200) {
                return CommandResult.ok("查询成功", result.getData(), "CARD");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("查询商品失败: fid={}", fid, e);
            return CommandResult.fail("查询商品失败: " + e.getMessage());
        }
    }
}