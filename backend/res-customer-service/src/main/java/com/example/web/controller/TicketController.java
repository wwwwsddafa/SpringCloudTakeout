package com.example.web.controller;

import com.example.bean.Ticket;
import com.example.exceptions.BizException;
import com.example.service.OperationLogService;
import com.example.service.TicketService;
import com.example.ticket.ResolutionType;
import com.example.ticket.TicketStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/customer-service/ticket")
@Slf4j
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping("/create")
    public ResultVo createTicket(@RequestBody Map<String, Object> request,
                                 @RequestHeader("X-User-Id") String creatorId) {
        String sessionId = (String) request.get("sessionId");
        String userId = (String) request.get("userId");
        String userName = (String) request.get("userName");
        String category = (String) request.get("category");
        String priority = (String) request.get("priority");
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String source = (String) request.get("source");

        if (userId == null || userId.isEmpty()) {
            throw new BizException(ResultCode.CS_USER_ID_EMPTY);
        }
        if (title == null || title.isEmpty()) {
            throw new BizException(ResultCode.CS_TICKET_TITLE_EMPTY);
        }

        Ticket ticket = ticketService.createTicket(
                sessionId, userId, userName, category, priority, title, description, source);
        return ResultVo.success(ticket);
    }

    @PostMapping("/assign/{ticketId}")
    public ResultVo assignTicket(@PathVariable String ticketId,
                                 @RequestHeader("X-User-Id") String agentId) {
        ticketService.assignTicket(ticketId, agentId);
        return ResultVo.success("工单已分配");
    }

    @PostMapping("/status/{ticketId}")
    public ResultVo updateStatus(@PathVariable String ticketId,
                                 @RequestBody Map<String, String> request) {
        String status = request.get("status");
        if (status == null) {
            throw new BizException(ResultCode.CS_STATUS_EMPTY);
        }
        try {
            ticketService.updateStatus(ticketId, TicketStatus.valueOf(status));
            return ResultVo.success("状态已更新");
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.CS_STATUS_INVALID.getCode(), "无效的状态: " + status);
        }
    }

    @PostMapping("/resolve/{ticketId}")
    public ResultVo resolveTicket(@PathVariable String ticketId,
                                  @RequestBody Map<String, String> request) {
        String resolution = request.get("resolution");
        String resolutionType = request.get("resolutionType");
        if (resolutionType == null) {
            throw new BizException(ResultCode.CS_RESOLUTION_EMPTY);
        }
        try {
            ticketService.resolveTicket(ticketId,
                    resolution != null ? resolution : "",
                    ResolutionType.valueOf(resolutionType));
            return ResultVo.success("工单已解决");
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.CS_RESOLUTION_INVALID.getCode(), "无效的解决类型: " + resolutionType);
        }
    }

    @PostMapping("/rate/{ticketId}")
    public ResultVo rateTicket(@PathVariable String ticketId,
                               @RequestBody Map<String, Object> request) {
        int rating = request.get("rating") != null ? ((Number) request.get("rating")).intValue() : 0;
        String comment = (String) request.get("comment");
        if (rating < 1 || rating > 5) {
            throw new BizException(ResultCode.CS_RATING_INVALID);
        }
        ticketService.rateTicket(ticketId, rating, comment);
        return ResultVo.success("评价已提交");
    }

    @PostMapping("/category/{ticketId}")
    public ResultVo updateCategory(@PathVariable String ticketId,
                                   @RequestBody Map<String, String> request) {
        String category = request.get("category");
        String priority = request.get("priority");
        ticketService.updateCategory(ticketId, category, priority);
        return ResultVo.success("分类已更新");
    }

    @PostMapping("/tags/{ticketId}")
    public ResultVo updateTags(@PathVariable String ticketId,
                               @RequestBody Map<String, String> request) {
        String tags = request.get("tags");
        ticketService.updateTags(ticketId, tags);
        return ResultVo.success("标签已更新");
    }

    @GetMapping("/detail/{ticketId}")
    public ResultVo getTicket(@PathVariable String ticketId) {
        Ticket ticket = ticketService.getTicket(ticketId);
        if (ticket == null) {
            throw new BizException(ResultCode.CS_TICKET_NOT_FOUND);
        }
        return ResultVo.success(ticket);
    }

    @GetMapping("/my")
    public ResultVo getMyTickets(@RequestHeader("X-User-Id") String agentId) {
        return ResultVo.success(ticketService.getTicketsByAgent(agentId));
    }

    @GetMapping("/user/{userId}")
    public ResultVo getTicketsByUser(@PathVariable String userId) {
        return ResultVo.success(ticketService.getTicketsByUser(userId));
    }

    @GetMapping("/pending")
    public ResultVo getPendingTickets() {
        return ResultVo.success(ticketService.getPendingTickets());
    }

    @GetMapping("/overdue")
    public ResultVo getOverdueTickets() {
        return ResultVo.success(ticketService.getOverdueTickets());
    }

    @GetMapping("/logs/{ticketId}")
    public ResultVo getOperationLogs(@PathVariable String ticketId) {
        return ResultVo.success(operationLogService.getLogsByTicket(ticketId));
    }
}