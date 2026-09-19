package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_offline_message")
public class OfflineMessage {

    @TableId("msg_id")
    private String msgId;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("content")
    private String content;

    @TableField("contact_info")
    private String contactInfo;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("handle_time")
    private LocalDateTime handleTime;
}