package com.example.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentFactory {

    @Autowired
    private HumanAgent humanAgent;

    @Autowired
    private AiAgent aiAgent;

    public Agent getAgent(AgentType type) {
        switch (type) {
            case AI:
                return aiAgent;
            case HUMAN:
            default:
                return humanAgent;
        }
    }

    public Agent getDefaultAgent() {
        return humanAgent;
    }

    public HumanAgent getHumanAgent() {
        return humanAgent;
    }

    public AiAgent getAiAgent() {
        return aiAgent;
    }
}