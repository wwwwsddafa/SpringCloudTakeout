package com.example.web.vo;


public enum UserRole {

    GUEST("访客"),
    USER("普通用户"),
    ADMIN("管理员");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static UserRole fromCode(String code) {
        if (code == null) {
            return GUEST;
        }
        for (UserRole role : values()) {
            if (role.name().equalsIgnoreCase(code)) {
                return role;
            }
        }
        return GUEST;
    }
}