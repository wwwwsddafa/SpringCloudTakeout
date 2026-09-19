package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("res_free_order_event")
public class ResFreeOrderEvent {

    @TableId("event_id")
    private String eventId;

    @TableField("event_name")
    private String eventName;

    @JsonAlias("couponCount")
    @TableField("total_count")
    private Integer totalCount;

    @TableField("remain_count")
    private Integer remainCount;

    @JsonAlias("couponMinAmount")
    @TableField("min_amount")
    private BigDecimal minAmount;

    @JsonAlias("couponMaxAmount")
    @TableField("max_amount")
    private BigDecimal maxAmount;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("admin_id")
    private String adminId;
}