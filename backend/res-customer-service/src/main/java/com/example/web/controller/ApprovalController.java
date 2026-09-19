package com.example.web.controller;

import com.example.command.CommandResult;
import com.example.exceptions.BizException;
import com.example.service.SensitiveOperationService;
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
@RequestMapping("/customer-service/approval")
@Slf4j
public class ApprovalController {

    @Autowired
    private SensitiveOperationService sensitiveOperationService;

    @GetMapping("/pending")
    public ResultVo getPendingApprovals() {
        return ResultVo.success(sensitiveOperationService.getPendingApprovals());
    }

    @PostMapping("/{approvalId}/approve")
    public ResultVo approve(@PathVariable String approvalId,
                            @RequestHeader("X-User-Id") String approverId) {
        CommandResult result = sensitiveOperationService.approve(approvalId, approverId);
        if (result.isSuccess()) {
            Map<String, Object> data = new HashMap<>();
            data.put("data", result.getData());
            data.put("displayType", result.getDisplayType() != null ? result.getDisplayType() : "TEXT");
            return ResultVo.success(200, result.getMessage(), data);
        } else {
            throw new BizException(ResultCode.CS_OPERATION_FAILED.getCode(), result.getMessage());
        }
    }

    @PostMapping("/{approvalId}/reject")
    public ResultVo reject(@PathVariable String approvalId,
                           @RequestHeader("X-User-Id") String approverId,
                           @RequestBody Map<String, String> request) {
        String reason = request.getOrDefault("reason", "无");
        sensitiveOperationService.reject(approvalId, approverId, reason);
        return ResultVo.success("审批已拒绝");
    }
}