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
public class SearchProductCommand implements Command {

    @Autowired
    private ProductApi productApi;

    @Override
    public String getName() {
        return "searchProduct";
    }

    @Override
    public String getDisplayName() {
        return "搜索商品";
    }

    @Override
    public String getDescription() {
        return "根据关键词搜索商品";
    }
//3 个 CommandParam 被装进同一个 List 里，它们共同构成了搜索命令的完整参数列表。
    @Override
    public List<CommandParam> getParams() {
        return List.of(
                CommandParam.builder()
                        .name("keyword")
                        .displayName("关键词")
                        .type("string")
                        .required(true)
                        .description("搜索关键词")
                        .build(),
                CommandParam.builder()
                        .name("page")
                        .displayName("页码")
                        .type("number")
                        .required(false)
                        .description("页码，默认1")
                        .build(),
                CommandParam.builder()
                        .name("size")
                        .displayName("每页数量")
                        .type("number")
                        .required(false)
                        .description("每页数量，默认10")
                        .build()
        );
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String keyword = (String) context.getParams().get("keyword");
        if (keyword == null || keyword.isEmpty()) {
            return CommandResult.fail("搜索关键词不能为空");
        }
        int page = 1;
        int size = 10;
        try {
            Object pageObj = context.getParams().get("page");
            if (pageObj != null) {
                page = pageObj instanceof Number ? ((Number) pageObj).intValue() : Integer.parseInt(pageObj.toString());
            }
            Object sizeObj = context.getParams().get("size");
            if (sizeObj != null) {
                size = sizeObj instanceof Number ? ((Number) sizeObj).intValue() : Integer.parseInt(sizeObj.toString());
            }
        } catch (NumberFormatException e) {
            return CommandResult.fail("页码或每页数量格式不正确");
        }
        try {
            ResultVo result = productApi.searchProducts(page, size, keyword);
            if (result.getCode() == 200) {
                //是给调用方（一般是前端 / 命令执行框架）指定返回数据的渲染格式类型。
                return CommandResult.ok("搜索成功", result.getData(), "TABLE");
            }
            return CommandResult.fail(result.getMsg());
        } catch (Exception e) {
            log.error("搜索商品失败: keyword={}", keyword, e);
            return CommandResult.fail("搜索商品失败: " + e.getMessage());
        }
    }
}