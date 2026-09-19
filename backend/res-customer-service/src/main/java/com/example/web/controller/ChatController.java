package com.example.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.api.FileUploadApi;
import com.example.bean.ChatMessage;
import com.example.command.Command;
import com.example.command.CommandRegistry;
import com.example.dao.mapper.ChatMessageMapper;
import com.example.exceptions.BizException;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/customer-service")
@Slf4j
public class ChatController {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private FileUploadApi fileUploadApi;

    @GetMapping("/history")
    public ResultVo getHistory(@RequestHeader("X-User-Id") String userId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .and(w -> w.eq(ChatMessage::getSenderId, userId)
                                .or().eq(ChatMessage::getReceiverId, userId))
                        .orderByAsc(ChatMessage::getCreateTime));

        return ResultVo.success(messages);
    }

    @GetMapping("/history/session/{sessionId}")
    public ResultVo getSessionHistory(@PathVariable String sessionId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime));

        return ResultVo.success(messages);
    }

    @GetMapping("/users")
    public ResultVo getActiveUsers() {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSenderRole, "user")
                        .orderByDesc(ChatMessage::getCreateTime));

        Set<String> userIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .collect(Collectors.toSet());

        return ResultVo.success(new ArrayList<>(userIds));
    }

    @GetMapping("/sessions/waiting")
    public ResultVo getWaitingSessions() {
        List<ChatSession> sessions = sessionManager.getWaitingSessions();
        return ResultVo.success(sessions);
    }

    @GetMapping("/sessions/my")
    public ResultVo getMySessions(@RequestHeader("X-User-Id") String agentId) {
        List<ChatSession> sessions = sessionManager.getActiveSessionsByAgent(agentId);
        return ResultVo.success(sessions);
    }

    @GetMapping("/commands")
    public ResultVo getAvailableCommands() {
        List<Command> commands = commandRegistry.getAllCommands();
        List<Object> result = commands.stream().map(cmd -> {
            return java.util.Map.of(
                    "name", cmd.getName(),
                    "displayName", cmd.getDisplayName(),
                    "description", cmd.getDescription(),
                    "params", cmd.getParams()
            );
        }).collect(Collectors.toList());
        return ResultVo.success(result);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultVo uploadImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(ResultCode.CS_FILE_EMPTY);
        }
        try {
            MultipartFile[] files = new MultipartFile[]{file};
            ResultVo uploadResult = fileUploadApi.upload(files);
            return uploadResult;
        } catch (Exception e) {
            log.error("上传文件失败: {}", e.getMessage(), e);
            throw new BizException(ResultCode.CS_FILE_UPLOAD_FAILED.getCode(), "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/history/user/{userId}")
    public ResultVo getUserHistory(@PathVariable String userId,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .and(w -> w.eq(ChatMessage::getSenderId, userId)
                                .or().eq(ChatMessage::getReceiverId, userId))
                        .orderByDesc(ChatMessage::getCreateTime));
        return ResultVo.success(messages);
    }

    @GetMapping("/sessions/active")
    public ResultVo getAllActiveSessions() {
        return ResultVo.success(sessionManager.getWaitingSessions());
    }
}