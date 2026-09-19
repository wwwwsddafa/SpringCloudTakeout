package com.example.web.vo;

import lombok.Data;

@Data
public class DeviceInfo {

    private String deviceType;

    private String browser;

    private String os;

    public static DeviceInfo empty() {
        return new DeviceInfo();
    }
}