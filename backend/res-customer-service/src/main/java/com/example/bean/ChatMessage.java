package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_chat_message")
public class ChatMessage {

    @TableId("msg_id")
    private String msgId;

    @TableField("sender_id")
    private String senderId;

    @TableField("sender_role")
    private String senderRole;

    @TableField("receiver_id")
    private String receiverId;

    @TableField("receiver_role")
    private String receiverRole;

    private String content;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("session_id")
    private String sessionId;

    @TableField("msg_type")
    private String msgType;

    @TableField("is_read")
    private Integer isRead;
}