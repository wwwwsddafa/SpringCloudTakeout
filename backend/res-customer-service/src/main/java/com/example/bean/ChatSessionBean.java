package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_chat_session")
public class ChatSessionBean {

    @TableId("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("agent_id")
    private String agentId;

    @TableField("agent_type")
    private String agentType;

    @TableField("status")
    private String status;

    @TableField("source")
    private String source;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("close_time")
    private LocalDateTime closeTime;
}