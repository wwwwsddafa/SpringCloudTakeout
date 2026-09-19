package com.example.agent;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentStatus {

    private String agentId;

    private String agentName;

    private AgentStatusEnum status;

    private AgentType agentType;

    private String skillGroup;

    private int currentSessions;

    private int maxSessions;

    private int totalHandled;

    private double avgRating;

    private long avgResponseSeconds;

    private LocalDateTime lastActiveTime;

    private LocalDateTime onlineTime;

    public boolean canAcceptNewSession() {
        return (status == AgentStatusEnum.ONLINE || status == AgentStatusEnum.BUSY)
                && currentSessions < maxSessions;
    }

    public void incrementSession() {
        this.currentSessions++;
        if (this.currentSessions >= this.maxSessions) {
            this.status = AgentStatusEnum.BUSY;
        }
    }

    public void decrementSession() {
        this.currentSessions = Math.max(0, this.currentSessions - 1);
        if (this.currentSessions < this.maxSessions && this.status == AgentStatusEnum.BUSY) {
            this.status = AgentStatusEnum.ONLINE;
        }
    }
}