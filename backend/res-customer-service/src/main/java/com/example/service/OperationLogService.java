package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bean.OperationLog;
import com.example.dao.mapper.OperationLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    public void log(String ticketId, String sessionId,
                    String operatorId, String operatorName, String operatorRole,
                    String operationType, Object params, Object result,
                    String targetUserId) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setLogId("LOG" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            logEntry.setTicketId(ticketId);
            logEntry.setSessionId(sessionId);
            logEntry.setOperatorId(operatorId);
            logEntry.setOperatorName(operatorName);
            logEntry.setOperatorRole(operatorRole);
            logEntry.setOperationType(operationType);
            logEntry.setOperationParams(params != null ? objectMapper.writeValueAsString(params) : null);
            logEntry.setOperationResult(result != null ? objectMapper.writeValueAsString(result) : null);
            logEntry.setTargetUserId(targetUserId);
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(logEntry);
            log.info("操作审计记录: operator={}, type={}, target={}", operatorId, operationType, targetUserId);
        } catch (JsonProcessingException e) {
            log.error("操作审计记录序列化失败: {}", e.getMessage());
        }
    }

    public java.util.List<OperationLog> getLogsByTicket(String ticketId) {
        return operationLogMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getTicketId, ticketId)
                        .orderByDesc(OperationLog::getCreateTime));
    }
}