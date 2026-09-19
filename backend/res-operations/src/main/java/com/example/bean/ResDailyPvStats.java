package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("res_daily_pv_stats")
public class ResDailyPvStats {

    @TableId
    private String id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("page_type")
    private String pageType;

    @TableField("fid")
    private String fid;

    @TableField("pv_count")
    private Long pvCount;

    @TableField("uv_count")
    private Long uvCount;

    @TableField("avg_stay_seconds")
    private Integer avgStaySeconds;

    @TableField("device_pc")
    private Long devicePc;

    @TableField("device_mobile")
    private Long deviceMobile;

    @TableField("device_tablet")
    private Long deviceTablet;

    @TableField("create_time")
    private LocalDateTime createTime;
}