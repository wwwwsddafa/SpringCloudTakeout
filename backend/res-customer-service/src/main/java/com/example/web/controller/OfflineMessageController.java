package com.example.web.controller;

import com.example.bean.OfflineMessage;
import com.example.exceptions.BizException;
import com.example.service.OfflineMessageService;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/customer-service/offline-message")
@Slf4j
public class OfflineMessageController {

    @Autowired
    private OfflineMessageService offlineMessageService;

    @PostMapping
    public ResultVo create(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String userName = request.get("userName");
        String content = request.get("content");
        String contactInfo = request.get("contactInfo");

        if (userId == null || userId.isEmpty()) {
            throw new BizException(ResultCode.CS_USER_ID_EMPTY);
        }
        if (content == null || content.isEmpty()) {
            throw new BizException(ResultCode.CS_MESSAGE_EMPTY);
        }

        OfflineMessage msg = offlineMessageService.create(userId, userName, content, contactInfo);
        return ResultVo.success(msg);
    }

    @PostMapping("/{msgId}/read")
    public ResultVo markAsRead(@PathVariable String msgId) {
        offlineMessageService.markAsRead(msgId);
        return ResultVo.success("已标记为已读");
    }

    @GetMapping("/unread")
    public ResultVo getUnreadMessages() {
        return ResultVo.success(offlineMessageService.getUnreadMessages());
    }

    @GetMapping("/user/{userId}")
    public ResultVo getMessagesByUser(@PathVariable String userId) {
        return ResultVo.success(offlineMessageService.getMessagesByUser(userId));
    }

    @GetMapping("/unread-count")
    public ResultVo getUnreadCount() {
        return ResultVo.success(offlineMessageService.getUnreadCount());
    }
}