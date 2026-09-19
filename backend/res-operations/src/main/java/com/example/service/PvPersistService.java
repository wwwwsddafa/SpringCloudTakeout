package com.example.service;

import com.example.web.vo.DeviceInfo;

public interface PvPersistService {

    void asyncSave(String userId, String sessionId, String pageUrl, String pageType,
                   String fid, DeviceInfo device, String ip, String referer, Integer staySeconds);
}