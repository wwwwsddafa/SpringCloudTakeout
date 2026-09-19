package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_ticket")
public class Ticket {

    @TableId("ticket_id")
    private String ticketId;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("category")
    private String category;

    @TableField("priority")
    private String priority;

    @TableField("status")
    private String status;

    @TableField("agent_id")
    private String agentId;

    @TableField("skill_group")
    private String skillGroup;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("sla_deadline")
    private LocalDateTime slaDeadline;

    @TableField("resolution")
    private String resolution;

    @TableField("resolution_type")
    private String resolutionType;

    @TableField("rating")
    private Integer rating;

    @TableField("rating_comment")
    private String ratingComment;

    @TableField("tags")
    private String tags;

    @TableField("related_order_id")
    private String relatedOrderId;

    @TableField("related_product_id")
    private String relatedProductId;

    @TableField("source")
    private String source;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("assign_time")
    private LocalDateTime assignTime;

    @TableField("resolve_time")
    private LocalDateTime resolveTime;

    @TableField("close_time")
    private LocalDateTime closeTime;
}