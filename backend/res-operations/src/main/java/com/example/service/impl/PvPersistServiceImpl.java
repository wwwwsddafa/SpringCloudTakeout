package com.example.service.impl;

import com.example.dao.mapper.PageViewMapper;
import com.example.service.PvPersistService;
import com.example.web.vo.DeviceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class PvPersistServiceImpl implements PvPersistService {

    @Autowired
    private PageViewMapper pageViewMapper;

    @Async
    @Override
    public void asyncSave(String userId, String sessionId, String pageUrl, String pageType,
                          String fid, DeviceInfo device, String ip, String referer, Integer staySeconds) {
        try {
            com.example.bean.ResPageView pv = new com.example.bean.ResPageView();
            pv.setId(UUID.randomUUID().toString());
            pv.setUserId(userId);
            pv.setSessionId(sessionId);
            pv.setPageUrl(truncate(pageUrl, 2048));
            pv.setPageType(pageType);
            pv.setFid(fid);
            pv.setDeviceType(device.getDeviceType());
            pv.setBrowser(device.getBrowser());
            pv.setOs(device.getOs());
            pv.setIp(ip);
            pv.setReferer(truncate(referer, 2048));
            pv.setStaySeconds(staySeconds);
            pv.setCreateTime(LocalDateTime.now());
            pageViewMapper.insert(pv);
        } catch (Exception e) {
            log.error("PV 明细写入 MySQL 失败", e);
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}