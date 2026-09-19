package com.example.config;

import com.example.agent.AgentFactory;
import com.example.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AgentConfig {

    @Bean
    public AgentType defaultAgentType() {
        log.info("默认客服类型: AI");
        return AgentType.AI;
    }
}