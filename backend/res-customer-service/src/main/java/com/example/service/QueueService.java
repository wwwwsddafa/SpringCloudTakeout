package com.example.service;

import com.example.agent.AgentStatus;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QueueService {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AgentStatusService agentStatusService;

    public int getQueuePosition(String userId) {
        ChatSession session = sessionManager.findByUserId(userId);
        if (session == null) {
            return -1;
        }
        List<ChatSession> waitingSessions = sessionManager.getWaitingSessions();
        for (int i = 0; i < waitingSessions.size(); i++) {
            if (waitingSessions.get(i).getUserId().equals(userId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public int getQueueSize() {
        return sessionManager.getWaitingSessions().size();
    }

    public int getEstimatedWaitMinutes(String userId) {
        int position = getQueuePosition(userId);
        if (position <= 0) {
            return 0;
        }
        List<AgentStatus> availableAgents = agentStatusService.getAvailableAgents(null);
        int agentCount = availableAgents.size();
        if (agentCount == 0) {
            return position * 5;
        }
        return Math.max(1, position / agentCount * 3);
    }

    public Map<String, Object> getQueueInfo(String userId) {
        Map<String, Object> info = new LinkedHashMap<>();
        int position = getQueuePosition(userId);
        info.put("position", position);
        info.put("queueSize", getQueueSize());
        info.put("estimatedWaitMinutes", getEstimatedWaitMinutes(userId));
        info.put("onlineAgentCount", agentStatusService.getOnlineAgents().size());
        return info;
    }

    public String autoAssignToAgent() {
        List<ChatSession> waitingSessions = sessionManager.getWaitingSessions();
        if (waitingSessions.isEmpty()) {
            return null;
        }
        List<AgentStatus> availableAgents = agentStatusService.getAvailableAgents(null);
        if (availableAgents.isEmpty()) {
            return null;
        }
        AgentStatus bestAgent = availableAgents.get(0);
        ChatSession session = waitingSessions.get(0);
        sessionManager.assignAgent(session.getSessionId(), bestAgent.getAgentId(),
                bestAgent.getAgentType());
        agentStatusService.incrementSession(bestAgent.getAgentId());
        log.info("自动分配: sessionId={}, agentId={}", session.getSessionId(), bestAgent.getAgentId());
        return bestAgent.getAgentId();
    }
}