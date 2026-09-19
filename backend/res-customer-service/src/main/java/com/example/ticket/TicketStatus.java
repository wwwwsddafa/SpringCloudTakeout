package com.example.ticket;

public enum TicketStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    RESOLVED("已解决"),
    CLOSED("已关闭"),
    FOLLOW_UP("待跟进");

    private final String description;

    TicketStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}