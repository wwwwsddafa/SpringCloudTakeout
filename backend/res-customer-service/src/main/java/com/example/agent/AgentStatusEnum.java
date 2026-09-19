package com.example.agent;

public enum AgentStatusEnum {
    ONLINE("在线"),
    BUSY("忙碌"),
    BREAK("小休"),
    OFFLINE("离线");

    private final String description;

    AgentStatusEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}