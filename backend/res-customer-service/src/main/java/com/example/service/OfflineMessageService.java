package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bean.OfflineMessage;
import com.example.dao.mapper.OfflineMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class OfflineMessageService {

    @Autowired
    private OfflineMessageMapper offlineMessageMapper;

    public OfflineMessage create(String userId, String userName, String content, String contactInfo) {
        OfflineMessage msg = new OfflineMessage();
        msg.setMsgId("OM" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        msg.setUserId(userId);
        msg.setUserName(userName);
        msg.setContent(content);
        msg.setContactInfo(contactInfo);
        msg.setStatus("UNREAD");
        msg.setCreateTime(LocalDateTime.now());
        offlineMessageMapper.insert(msg);
        log.info("离线留言创建: msgId={}, userId={}", msg.getMsgId(), userId);
        return msg;
    }

    public void markAsRead(String msgId) {
        OfflineMessage msg = offlineMessageMapper.selectById(msgId);
        if (msg != null) {
            msg.setStatus("READ");
            msg.setHandleTime(LocalDateTime.now());
            offlineMessageMapper.updateById(msg);
        }
    }

    public List<OfflineMessage> getUnreadMessages() {
        return offlineMessageMapper.selectList(
                new LambdaQueryWrapper<OfflineMessage>()
                        .eq(OfflineMessage::getStatus, "UNREAD")
                        .orderByDesc(OfflineMessage::getCreateTime));
    }

    public List<OfflineMessage> getMessagesByUser(String userId) {
        return offlineMessageMapper.selectList(
                new LambdaQueryWrapper<OfflineMessage>()
                        .eq(OfflineMessage::getUserId, userId)
                        .orderByDesc(OfflineMessage::getCreateTime));
    }

    public int getUnreadCount() {
        return offlineMessageMapper.selectCount(
                new LambdaQueryWrapper<OfflineMessage>()
                        .eq(OfflineMessage::getStatus, "UNREAD")).intValue();
    }
}