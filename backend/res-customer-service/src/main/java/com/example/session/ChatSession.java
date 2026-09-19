package com.example.session;

import com.example.agent.AgentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    private String sessionId;

    private String userId;

    private String userName;

    private String agentId;

    private AgentType agentType;

    private SessionStatus status;

    private String source;

    private String ticketId;

    private String ticketCategory;

    private String ticketPriority;

    private String sessionTags;

    private Integer rating;

    private String ratingComment;

    private LocalDateTime createTime;

    private LocalDateTime closeTime;

    @Builder.Default
    private List<String> unreadMessageIds = new CopyOnWriteArrayList<>();

    public void addUnreadMessage(String msgId) {
        unreadMessageIds.add(msgId);
    }

    public int getUnreadCount() {
        return unreadMessageIds.size();
    }

    public void clearUnread() {
        unreadMessageIds.clear();
    }
}