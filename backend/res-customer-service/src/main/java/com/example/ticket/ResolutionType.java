package com.example.ticket;

public enum ResolutionType {
    REFUNDED("已退款"),
    EXCHANGED("已换货"),
    COMPENSATED("已补偿"),
    ADVISED("已解答"),
    ESCALATED("已升级"),
    UNRESOLVED("未解决");

    private final String description;

    ResolutionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}