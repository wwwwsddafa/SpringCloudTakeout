package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_page_view")
public class ResPageView {

    @TableId
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("page_url")
    private String pageUrl;

    @TableField("page_type")
    private String pageType;

    @TableField("fid")
    private String fid;

    @TableField("device_type")
    private String deviceType;

    @TableField("browser")
    private String browser;

    @TableField("os")
    private String os;

    @TableField("ip")
    private String ip;

    @TableField("referer")
    private String referer;

    @TableField("stay_seconds")
    private Integer staySeconds;

    @TableField("create_time")
    private LocalDateTime createTime;
}