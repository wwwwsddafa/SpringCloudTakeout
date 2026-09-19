package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_quick_reply")
public class QuickReply {

    @TableId("template_id")
    private String templateId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("category")
    private String category;

    @TableField("skill_group")
    private String skillGroup;

    @TableField("is_shared")
    private Integer isShared;

    @TableField("usage_count")
    private Integer usageCount;

    @TableField("creator_id")
    private String creatorId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}