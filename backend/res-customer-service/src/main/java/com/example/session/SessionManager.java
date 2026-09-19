package com.example.session;

import com.example.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SessionManager {

    private final ConcurrentHashMap<String, ChatSession> sessionMap = new ConcurrentHashMap<>();

    public ChatSession createSession(String userId, String userName) {
        ChatSession existing = findByUserId(userId);
        if (existing != null && existing.getStatus() != SessionStatus.CLOSED) {
            return existing;
        }
        ChatSession session = ChatSession.builder()
                .sessionId("SES" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .userId(userId)
                .userName(userName)
                .status(SessionStatus.WAITING)
                .createTime(LocalDateTime.now())
                .build();
        sessionMap.put(session.getSessionId(), session);
        log.info("会话创建: sessionId={}, userId={}", session.getSessionId(), userId);
        return session;
    }

    public void assignAgent(String sessionId, String agentId, AgentType agentType) {
        ChatSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        session.setAgentId(agentId);
        session.setAgentType(agentType);
        session.setStatus(SessionStatus.ACTIVE);
        log.info("会话分配客服: sessionId={}, agentId={}, agentType={}", sessionId, agentId, agentType);
    }

    public ChatSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    public ChatSession findByUserId(String userId) {
        return sessionMap.values().stream()
                .filter(s -> s.getUserId().equals(userId) && s.getStatus() != SessionStatus.CLOSED)
                .findFirst()
                .orElse(null);
    }
//查询某个客服当前正在服务的所有活跃会话。
    public List<ChatSession> getActiveSessionsByAgent(String agentId) {
        return sessionMap.values().stream()
                .filter(s -> agentId.equals(s.getAgentId()) && s.getStatus() == SessionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public List<ChatSession> getWaitingSessions() {
        return sessionMap.values().stream()
                .filter(s -> s.getStatus() == SessionStatus.WAITING)
                .collect(Collectors.toList());
    }

    public void closeSession(String sessionId) {
        ChatSession session = sessionMap.get(sessionId);
        if (session != null) {
            session.setStatus(SessionStatus.CLOSED);
            session.setCloseTime(LocalDateTime.now());
            log.info("会话关闭: sessionId={}", sessionId);
            sessionMap.remove(sessionId);
        }
    }

    public void transferSession(String sessionId, String targetAgentId) {
        ChatSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        String oldAgentId = session.getAgentId();
        session.setAgentId(targetAgentId);
        if (targetAgentId == null) {
            session.setStatus(SessionStatus.WAITING);
        } else {
            session.setStatus(SessionStatus.TRANSFERRED);
        }
        log.info("会话转接: sessionId={}, from={}, to={}, status={}", sessionId, oldAgentId, targetAgentId, session.getStatus());
    }

    public void bindTicket(String sessionId, String ticketId) {
        ChatSession session = sessionMap.get(sessionId);
        if (session != null) {
            session.setTicketId(ticketId);
            log.info("会话绑定工单: sessionId={}, ticketId={}", sessionId, ticketId);
        }
    }

    public void setTicketCategory(String sessionId, String category, String priority) {
        ChatSession session = sessionMap.get(sessionId);
        if (session != null) {
            session.setTicketCategory(category);
            session.setTicketPriority(priority);
            log.info("会话工单分类: sessionId={}, category={}, priority={}", sessionId, category, priority);
        }
    }

    public void setSessionTags(String sessionId, String tags) {
        ChatSession session = sessionMap.get(sessionId);
        if (session != null) {
            session.setSessionTags(tags);
            log.info("会话标签: sessionId={}, tags={}", sessionId, tags);
        }
    }

    public void rateSession(String sessionId, int rating, String comment) {
        ChatSession session = sessionMap.get(sessionId);
        if (session != null) {
            session.setRating(rating);
            session.setRatingComment(comment);
            log.info("会话评价: sessionId={}, rating={}", sessionId, rating);
        }
    }
}