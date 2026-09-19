package com.example.service;

import com.example.agent.AgentStatus;
import com.example.agent.AgentStatusEnum;
import com.example.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentStatusService {

    private final ConcurrentHashMap<String, AgentStatus> agentStatusMap = new ConcurrentHashMap<>();

    public void agentOnline(String agentId, String agentName, AgentType agentType,
                            String skillGroup, int maxSessions) {
        AgentStatus status = new AgentStatus();
        status.setAgentId(agentId);
        status.setAgentName(agentName);
        status.setStatus(AgentStatusEnum.ONLINE);
        status.setAgentType(agentType);
        status.setSkillGroup(skillGroup);
        status.setMaxSessions(maxSessions);
        status.setCurrentSessions(0);
        status.setOnlineTime(LocalDateTime.now());
        status.setLastActiveTime(LocalDateTime.now());
        agentStatusMap.put(agentId, status);
        log.info("客服上线: agentId={}, name={}, skillGroup={}", agentId, agentName, skillGroup);
    }

    public void agentOffline(String agentId) {
        AgentStatus status = agentStatusMap.get(agentId);
        if (status != null) {
            status.setStatus(AgentStatusEnum.OFFLINE);
            status.setCurrentSessions(0);
        }
        agentStatusMap.remove(agentId);
        log.info("客服下线: agentId={}", agentId);
    }

    public void updateStatus(String agentId, AgentStatusEnum newStatus) {
        AgentStatus status = agentStatusMap.get(agentId);
        if (status != null) {
            status.setStatus(newStatus);
            status.setLastActiveTime(LocalDateTime.now());
            log.info("客服状态变更: agentId={}, status={}", agentId, newStatus);
        }
    }

    public void incrementSession(String agentId) {
        AgentStatus status = agentStatusMap.get(agentId);
        if (status != null) {
            status.incrementSession();
            log.info("客服会话+1: agentId={}, current={}", agentId, status.getCurrentSessions());
        }
    }

    public void decrementSession(String agentId) {
        AgentStatus status = agentStatusMap.get(agentId);
        if (status != null) {
            status.decrementSession();
            log.info("客服会话-1: agentId={}, current={}", agentId, status.getCurrentSessions());
        }
    }

    public AgentStatus getAgentStatus(String agentId) {
        return agentStatusMap.get(agentId);
    }

    public List<AgentStatus> getOnlineAgents() {
        return agentStatusMap.values().stream()
                .filter(s -> s.getStatus() != AgentStatusEnum.OFFLINE)
                .collect(Collectors.toList());
    }

    public List<AgentStatus> getAvailableAgents(String skillGroup) {
        return agentStatusMap.values().stream()
                .filter(AgentStatus::canAcceptNewSession)
                .filter(s -> skillGroup == null || skillGroup.equals(s.getSkillGroup()))
                .sorted((a, b) -> Integer.compare(a.getCurrentSessions(), b.getCurrentSessions()))
                .collect(Collectors.toList());
    }

    public Collection<AgentStatus> getAllAgentStatus() {
        return new ArrayList<>(agentStatusMap.values());
    }

    public boolean isOnline(String agentId) {
        AgentStatus status = agentStatusMap.get(agentId);
        return status != null && status.getStatus() != AgentStatusEnum.OFFLINE;
    }
}