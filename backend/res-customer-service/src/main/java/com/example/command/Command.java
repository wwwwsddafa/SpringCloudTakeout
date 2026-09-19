package com.example.command;

import com.example.agent.Agent;

import java.util.List;

public interface Command {

    String getName();

    String getDisplayName();

    String getDescription();

    List<CommandParam> getParams();

    CommandResult execute(CommandContext context);

    default boolean canExecute(Agent agent) {
        return true;
    }
}