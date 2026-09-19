package com.example.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult {
    private boolean success;
    private String message;
    private Object data;
    private String errorCode;
    private String displayType;
    private boolean needApproval;

    public static CommandResult ok(String message) {
        return CommandResult.builder().success(true).message(message).displayType("TEXT").build();
    }

    public static CommandResult ok(String message, Object data) {
        return CommandResult.builder().success(true).message(message).data(data).displayType("CARD").build();
    }

    public static CommandResult ok(String message, Object data, String displayType) {
        return CommandResult.builder().success(true).message(message).data(data).displayType(displayType).build();
    }

    public static CommandResult fail(String message) {
        return CommandResult.builder().success(false).message(message).displayType("TEXT").build();
    }

    public static CommandResult fail(String message, String errorCode) {
        return CommandResult.builder().success(false).message(message).errorCode(errorCode).displayType("TEXT").build();
    }
}