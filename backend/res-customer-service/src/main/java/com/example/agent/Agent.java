package com.example.agent;

import com.example.bean.ChatMessage;
import com.example.command.Command;
import com.example.command.CommandContext;
import com.example.command.CommandResult;
import com.example.session.ChatSession;

import java.util.List;

public interface Agent {

    void handleMessage(ChatMessage message, ChatSession session);

    CommandResult executeCommand(Command command, CommandContext context);

    List<AgentCapability> getCapabilities();

    AgentType getType();
}