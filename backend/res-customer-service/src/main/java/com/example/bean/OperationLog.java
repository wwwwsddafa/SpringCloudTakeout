package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_operation_log")
public class OperationLog {

    @TableId("log_id")
    private String logId;

    @TableField("ticket_id")
    private String ticketId;

    @TableField("session_id")
    private String sessionId;

    @TableField("operator_id")
    private String operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("operator_role")
    private String operatorRole;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_params")
    private String operationParams;

    @TableField("operation_result")
    private String operationResult;

    @TableField("target_user_id")
    private String targetUserId;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("create_time")
    private LocalDateTime createTime;
}