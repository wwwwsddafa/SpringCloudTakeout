package com.example.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandRegistry {

    private final Map<String, Command> commandMap = new HashMap<>();

    @Autowired
    public CommandRegistry(List<Command> commands) {
        for (Command command : commands) {
            commandMap.put(command.getName(), command);
            log.info("注册命令: {} -> {}", command.getName(), command.getDisplayName());
        }
        log.info("命令注册完成，共注册 {} 个命令", commandMap.size());
    }

    public Command getCommand(String name) {
        return commandMap.get(name);
    }

    public List<Command> getAllCommands() {
        return commandMap.values().stream().collect(Collectors.toList());
    }

    public CommandResult execute(String commandName, CommandContext context) {
        Command command = commandMap.get(commandName);
        if (command == null) {
            log.warn("未知命令: commandName={}, userId={}, sessionId={}", commandName, context.getUserId(), context.getSessionId());
            return CommandResult.fail("未知命令: " + commandName);
        }
        try {
            CommandResult result = command.execute(context);
            if (result.isSuccess()) {
                log.info("命令执行成功: {} -> {}, 操作类型: {}, data={}", commandName, command.getDisplayName(), getOperationType(commandName), result.getData());
            } else {
                log.warn("命令执行失败: {} -> {}, 操作类型: {}, 原因: errorCode={}, message={}, 参数: {}",
                        commandName, command.getDisplayName(), getOperationType(commandName),
                        result.getErrorCode(), result.getMessage(), context.getParams());
            }
            return result;
        } catch (Exception e) {
            log.error("命令执行异常: {} -> {}, 操作类型: {}, 异常类型: {}, 异常信息: {}, 参数: {}",
                    commandName, command.getDisplayName(), getOperationType(commandName),
                    e.getClass().getSimpleName(), e.getMessage(), context.getParams(), e);
            return CommandResult.fail("命令执行失败: " + e.getMessage());
        }
    }

    private String getOperationType(String commandName) {
        String lower = commandName.toLowerCase();
        if (lower.startsWith("query") || lower.startsWith("list") || lower.startsWith("search")) {
            return "查询操作";
        }
        if (lower.startsWith("modify") || lower.startsWith("update") || lower.startsWith("edit")) {
            return "修改操作";
        }
        if (lower.startsWith("cancel")) {
            return "取消操作";
        }
        if (lower.startsWith("refund")) {
            return "退单操作";
        }
        if (lower.startsWith("add") || lower.startsWith("create")) {
            return "新增操作";
        }
        return "其他操作";
    }
}