package com.example.service;

import com.example.command.CommandContext;
import com.example.command.CommandRegistry;
import com.example.command.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SensitiveOperationService {

    private static final String RISK_LEVEL_LOW = "LOW";
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";
    private static final String RISK_LEVEL_HIGH = "HIGH";

    private final ConcurrentHashMap<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private OperationLogService operationLogService;

    public static class PendingApproval {
        public String approvalId;
        public String sessionId;
        public String commandName;
        public Map<String, Object> params;
        public String operatorId;
        public String operatorName;
        public String targetUserId;
        public String riskLevel;
        public long createdAt;

        public PendingApproval(String approvalId, String sessionId, String commandName,
                               Map<String, Object> params, String operatorId, String operatorName,
                               String targetUserId, String riskLevel) {
            this.approvalId = approvalId;
            this.sessionId = sessionId;
            this.commandName = commandName;
            this.params = params;
            this.operatorId = operatorId;
            this.operatorName = operatorName;
            this.targetUserId = targetUserId;
            this.riskLevel = riskLevel;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public String assessRisk(String commandName, Map<String, Object> params) {
        return RISK_LEVEL_LOW;
    }

    public boolean requiresApproval(String commandName, Map<String, Object> params) {
        return false;
    }

    public String createApproval(String sessionId, String commandName,
                                 Map<String, Object> params, String operatorId,
                                 String operatorName, String targetUserId) {
        String approvalId = "APR" + System.currentTimeMillis();
        String riskLevel = assessRisk(commandName, params);
        PendingApproval approval = new PendingApproval(
                approvalId, sessionId, commandName, params, operatorId, operatorName, targetUserId, riskLevel);
        pendingApprovals.put(approvalId, approval);
        log.info("创建审批: approvalId={}, commandName={}, riskLevel={}, operatorId={}",
                approvalId, commandName, riskLevel, operatorId);
        return approvalId;
    }

    public PendingApproval getApproval(String approvalId) {
        return pendingApprovals.get(approvalId);
    }

    public CommandResult approve(String approvalId, String approverId) {
        PendingApproval approval = pendingApprovals.remove(approvalId);
        if (approval == null) {
            log.warn("审批单不存在或已处理: approvalId={}", approvalId);
            return CommandResult.fail("审批单不存在或已处理");
        }

        log.info("审批通过，开始执行: approvalId={}, commandName={}, targetUserId={}",
                approvalId, approval.commandName, approval.targetUserId);

        CommandContext context = CommandContext.builder()
                .userId(approval.targetUserId)
                .userName(approval.operatorName)
                .agentId(approval.operatorId)
                .sessionId(approval.sessionId)
                .params(approval.params)
                .build();

        CommandResult result = commandRegistry.execute(approval.commandName, context);

        operationLogService.log(
                null, approval.sessionId,
                approval.operatorId, approval.operatorName, "admin",
                approval.commandName + "(审批通过)", approval.params, result,
                approval.targetUserId);

        log.info("审批命令执行完成: approvalId={}, commandName={}, success={}",
                approvalId, approval.commandName, result.isSuccess());
        return result;
    }

    public void reject(String approvalId, String approverId, String reason) {
        PendingApproval approval = pendingApprovals.remove(approvalId);
        if (approval != null) {
            log.info("审批拒绝: approvalId={}, approverId={}, commandName={}, reason={}",
                    approvalId, approverId, approval.commandName, reason);
        }
    }

    public java.util.Collection<PendingApproval> getPendingApprovals() {
        return pendingApprovals.values();
    }
}