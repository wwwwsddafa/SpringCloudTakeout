package com.example.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEnvelope {
    private String type;
    private String sessionId;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String content;
    private String commandName;
    private Map<String, Object> commandParams;
    private Map<String, Object> data;
    private String timestamp;
}