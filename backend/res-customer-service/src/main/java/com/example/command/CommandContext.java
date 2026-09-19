package com.example.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/*
* 携带命令执行时需要的实际数据
*
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandContext {
    private String userId;
    private String userName;
    private String agentId;
    private String sessionId;
    private Map<String, Object> params;
}