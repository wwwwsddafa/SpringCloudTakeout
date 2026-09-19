package com.example.web.controller;

import com.example.agent.AgentStatus;
import com.example.agent.AgentStatusEnum;
import com.example.agent.AgentType;
import com.example.service.AgentStatusService;
import com.example.service.QueueService;
import com.example.session.ChatSession;
import com.example.session.SessionManager;
import com.example.exceptions.BizException;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/customer-service/agent/workbench")
@Slf4j
public class AgentWorkbenchController {

    @Autowired
    private AgentStatusService agentStatusService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private QueueService queueService;

    @PostMapping("/online")
    public ResultVo agentOnline(@RequestHeader("X-User-Id") String agentId,
                                @RequestBody Map<String, Object> request) {
        String agentName = (String) request.getOrDefault("agentName", "客服_" + agentId);
        String skillGroup = (String) request.getOrDefault("skillGroup", "DEFAULT");
        int maxSessions = request.get("maxSessions") != null
                ? ((Number) request.get("maxSessions")).intValue() : 5;
        agentStatusService.agentOnline(agentId, agentName, AgentType.HUMAN, skillGroup, maxSessions);
        return ResultVo.success("已上线");
    }

    @PostMapping("/offline")
    public ResultVo agentOffline(@RequestHeader("X-User-Id") String agentId) {
        agentStatusService.agentOffline(agentId);
        return ResultVo.success("已下线");
    }

    @PostMapping("/status")
    public ResultVo updateStatus(@RequestHeader("X-User-Id") String agentId,
                                  @RequestBody Map<String, String> request) {
        String status = request.get("status");
        if (status == null) {
            throw new BizException(ResultCode.CS_STATUS_EMPTY);
        }
        try {
            agentStatusService.updateStatus(agentId, AgentStatusEnum.valueOf(status));
            return ResultVo.success("状态已更新");
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.CS_STATUS_INVALID.getCode(), "无效的状态: " + status);
        }
    }

    @GetMapping("/status")
    public ResultVo getMyStatus(@RequestHeader("X-User-Id") String agentId) {
        AgentStatus status = agentStatusService.getAgentStatus(agentId);
        if (status == null) {
            throw new BizException(ResultCode.CS_AGENT_NOT_ONLINE);
        }
        return ResultVo.success(status);
    }

    @GetMapping("/online-agents")
    public ResultVo getOnlineAgents() {
        return ResultVo.success(agentStatusService.getOnlineAgents());
    }

    @GetMapping("/my-sessions")
    public ResultVo getMySessions(@RequestHeader("X-User-Id") String agentId) {
        List<ChatSession> sessions = sessionManager.getActiveSessionsByAgent(agentId);
        return ResultVo.success(sessions);
    }

    @GetMapping("/waiting-sessions")
    public ResultVo getWaitingSessions() {
        return ResultVo.success(sessionManager.getWaitingSessions());
    }

    @GetMapping("/queue-info")
    public ResultVo getQueueInfo(@RequestParam(defaultValue = "") String userId) {
        if (!userId.isEmpty()) {
            return ResultVo.success(queueService.getQueueInfo(userId));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("queueSize", queueService.getQueueSize());
        info.put("onlineAgentCount", agentStatusService.getOnlineAgents().size());
        return ResultVo.success(info);
    }

    @PostMapping("/auto-assign")
    public ResultVo autoAssign() {
        String agentId = queueService.autoAssignToAgent();
        if (agentId == null) {
            return ResultVo.success("当前没有可分配的会话");
        }
        return ResultVo.success("已自动分配会话给客服: " + agentId);
    }

    @GetMapping("/dashboard")
    public ResultVo getDashboard(@RequestHeader("X-User-Id") String agentId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        AgentStatus status = agentStatusService.getAgentStatus(agentId);
        dashboard.put("agentStatus", status);
        dashboard.put("activeSessions", sessionManager.getActiveSessionsByAgent(agentId).size());
        dashboard.put("waitingCount", sessionManager.getWaitingSessions().size());
        dashboard.put("onlineAgentCount", agentStatusService.getOnlineAgents().size());
        return ResultVo.success(dashboard);
    }
}