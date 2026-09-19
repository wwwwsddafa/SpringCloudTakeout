package com.example.ticket;

public enum TicketPriority {
    URGENT("紧急"),
    HIGH("高"),
    MEDIUM("中"),
    LOW("低");

    private final String description;

    TicketPriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}