package com.example.web.controller;

import com.example.agent.Agent;
import com.example.agent.AgentFactory;
import com.example.agent.AgentType;
import com.example.agent.AiAgent;
import com.example.command.CommandContext;
import com.example.command.CommandRegistry;
import com.example.command.CommandResult;
import com.example.exceptions.BizException;
import com.example.service.LogisticsSimulator;
import com.example.service.OperationLogService;
import com.example.service.QueueService;
import com.example.service.SensitiveOperationService;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/customer-service/agent")
@Slf4j
public class AgentController {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private SensitiveOperationService sensitiveOperationService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private LogisticsSimulator logisticsSimulator;

    @PostMapping("/assign/{sessionId}")
    public ResultVo assignSession(@PathVariable String sessionId,
                                  @RequestHeader("X-User-Id") String agentId) {
        ChatSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.CS_SESSION_NOT_FOUND);
        }
        sessionManager.assignAgent(sessionId, agentId, AgentType.HUMAN);
        return ResultVo.success("会话已接入");
    }

    @PostMapping("/close/{sessionId}")
    public ResultVo closeSession(@PathVariable String sessionId) {
        sessionManager.closeSession(sessionId);
        return ResultVo.success("会话已关闭");
    }

    @PostMapping("/transfer/{sessionId}")
    public ResultVo transferSession(@PathVariable String sessionId,
                                    @RequestBody Map<String, String> request) {
        String targetAgentId = request.get("targetAgentId");
        if (targetAgentId == null || targetAgentId.isEmpty()) {
            throw new BizException(ResultCode.CS_AGENT_ID_EMPTY);
        }
        sessionManager.transferSession(sessionId, targetAgentId);
        return ResultVo.success("会话已转接");
    }

    /**
     * AI 转人工工具接口 —— 供 Python AI 服务调用
     * 当 AI 判断无法处理用户问题时，作为 function call 调用此接口
     */
    @PostMapping("/ai-transfer")
    public ResultVo aiTransferToHuman(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String userId = request.get("userId");
        String reason = request.getOrDefault("reason", "AI 无法处理该问题");

        if (sessionId == null || sessionId.isEmpty()) {
            throw new BizException(ResultCode.CS_SESSION_ID_EMPTY);
        }
        if (userId == null || userId.isEmpty()) {
            throw new BizException(ResultCode.CS_USER_ID_EMPTY);
        }

        ChatSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.CS_SESSION_NOT_FOUND);
        }

        if (!"ai_agent".equals(session.getAgentId())) {
            throw new BizException(ResultCode.CS_NOT_AI_SESSION);
        }

        sessionManager.transferSession(sessionId, null);

        String assignedAgentId = queueService.autoAssignToAgent();

        AiAgent aiAgent = (AiAgent) agentFactory.getAiAgent();
        aiAgent.notifyUserTransfer(userId, sessionId, reason, assignedAgentId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("assignedAgentId", assignedAgentId);
        result.put("status", assignedAgentId != null ? "assigned" : "queued");
        result.put("reason", reason);

        log.info("AI 转人工 API 调用: sessionId={}, userId={}, assignedAgent={}, reason={}",
                sessionId, userId, assignedAgentId, reason);

        return ResultVo.success(200, "已转接人工客服", result);
    }

    @PostMapping("/bind-ticket/{sessionId}")
    public ResultVo bindTicket(@PathVariable String sessionId,
                               @RequestBody Map<String, String> request) {
        String ticketId = request.get("ticketId");
        if (ticketId == null || ticketId.isEmpty()) {
            throw new BizException(ResultCode.CS_TICKET_ID_EMPTY);
        }
        sessionManager.bindTicket(sessionId, ticketId);
        return ResultVo.success("工单已绑定");
    }

    @PostMapping("/category/{sessionId}")
    public ResultVo setCategory(@PathVariable String sessionId,
                                @RequestBody Map<String, String> request) {
        String category = request.get("category");
        String priority = request.get("priority");
        sessionManager.setTicketCategory(sessionId, category, priority);
        return ResultVo.success("分类已设置");
    }

    @PostMapping("/tags/{sessionId}")
    public ResultVo setTags(@PathVariable String sessionId,
                            @RequestBody Map<String, String> request) {
        String tags = request.get("tags");
        sessionManager.setSessionTags(sessionId, tags);
        return ResultVo.success("标签已设置");
    }

    @PostMapping("/rate/{sessionId}")
    public ResultVo rateSession(@PathVariable String sessionId,
                                @RequestBody Map<String, Object> request) {
        int rating = request.get("rating") != null ? ((Number) request.get("rating")).intValue() : 0;
        String comment = (String) request.get("comment");
        if (rating < 1 || rating > 5) {
            throw new BizException(ResultCode.CS_RATING_INVALID);
        }
        sessionManager.rateSession(sessionId, rating, comment);
        return ResultVo.success("评价已提交");
    }

    @PostMapping("/command/execute")
    public ResultVo executeCommand(@RequestBody Map<String, Object> request,
                                   @RequestHeader("X-User-Id") String agentId) {
        String commandName = (String) request.get("commandName");
        String targetUserId = (String) request.get("targetUserId");
        String sessionId = (String) request.get("sessionId");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");

        if (sensitiveOperationService.requiresApproval(commandName, params != null ? params : Map.of())) {
            String approvalId = sensitiveOperationService.createApproval(
                    sessionId, commandName, params, agentId, "客服", targetUserId);
            Map<String, Object> approvalData = new HashMap<>();
            approvalData.put("needApproval", true);
            approvalData.put("approvalId", approvalId);
            approvalData.put("message", "此操作需要审批，已生成审批单");
            return ResultVo.success(200, "需要审批", approvalData);
        }

        CommandContext context = CommandContext.builder()
                .userId(targetUserId)
                .agentId(agentId)
                .sessionId(sessionId)
                .params(params)
                .build();

        CommandResult result = commandRegistry.execute(commandName, context);

        operationLogService.log(
                null, sessionId,
                agentId, "客服", "admin",
                commandName, params, result,
                targetUserId);

        if (result.isSuccess()) {
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("success", true);
            resultData.put("message", result.getMessage());
            resultData.put("data", result.getData());
            resultData.put("displayType", result.getDisplayType() != null ? result.getDisplayType() : "TEXT");
            resultData.put("needApproval", result.isNeedApproval());
            return ResultVo.success(200, result.getMessage(), resultData);
        } else {
            throw new BizException(ResultCode.CS_OPERATION_FAILED.getCode(), result.getMessage());
        }
    }

    @GetMapping("/commands")
    public ResultVo getAvailableCommands() {
        return ResultVo.success(commandRegistry.getAllCommands().stream().map(cmd -> {
            return Map.of(
                    "name", cmd.getName(),
                    "displayName", cmd.getDisplayName(),
                    "description", cmd.getDescription(),
                    "params", cmd.getParams()
            );
        }).toList());
    }

    /**
     * 模拟物流状态 —— 管理员设置订单的配送进度
     */
    @PostMapping("/logistics/set")
    public ResultVo setLogistics(@RequestBody Map<String, String> request,
                                  @RequestHeader("X-User-Id") String agentId) {
        String roid = request.get("roid");
        String status = request.get("status");
        String logisticsCompany = request.get("logisticsCompany");
        String trackingNumber = request.get("trackingNumber");
        String riderName = request.get("riderName");
        String riderPhone = request.get("riderPhone");

        if (roid == null || roid.isEmpty()) {
            throw new BizException(ResultCode.CS_ORDER_NO_EMPTY);
        }
        if (status == null || status.isEmpty()) {
            throw new BizException(ResultCode.CS_LOGISTICS_STATUS_EMPTY);
        }

        try {
            LogisticsSimulator.DeliveryStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.CS_LOGISTICS_STATUS_INVALID.getCode(), "无效的物流状态: " + status
                    + "，可选值: " + java.util.Arrays.toString(
                            java.util.Arrays.stream(LogisticsSimulator.DeliveryStatus.values())
                                    .map(LogisticsSimulator.DeliveryStatus::getCode).toArray()));
        }

        logisticsSimulator.setLogistics(roid, status, logisticsCompany,
                trackingNumber, riderName, riderPhone);

        Map<String, Object> result = logisticsSimulator.buildLogisticsResponse(roid);

        operationLogService.log(null, null, agentId, "客服", "admin",
                "setLogistics", Map.of("roid", roid, "status", status),
                null, null);

        log.info("管理员设置物流状态: agentId={}, roid={}, status={}", agentId, roid, status);
        return ResultVo.success(200, "物流状态已更新", result);
    }

    /**
     * 查询物流状态（管理员视角，含可推进状态列表）
     */
    @GetMapping("/logistics/{roid}")
    public ResultVo getLogistics(@PathVariable String roid) {
        if (roid == null || roid.isEmpty()) {
            throw new BizException(ResultCode.CS_ORDER_NO_EMPTY);
        }
        Map<String, Object> result = logisticsSimulator.buildLogisticsResponse(roid);
        return ResultVo.success(200, "查询成功", result);
    }
}