package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("res_daily_order_stats")
public class ResDailyOrderStats {

    @TableId
    private String id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("total_orders")
    private Integer totalOrders;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("paid_orders")
    private Integer paidOrders;

    @TableField("paid_amount")
    private BigDecimal paidAmount;

    @TableField("cancelled_orders")
    private Integer cancelledOrders;

    @TableField("pending_orders")
    private Integer pendingOrders;

    @TableField("refunded_orders")
    private Integer refundedOrders;

    @TableField("avg_order_amount")
    private BigDecimal avgOrderAmount;

    @TableField("max_order_amount")
    private BigDecimal maxOrderAmount;

    @TableField("alipay_orders")
    private Integer alipayOrders;

    @TableField("wechat_orders")
    private Integer wechatOrders;

    @TableField("free_order_count")
    private Integer freeOrderCount;

    @TableField("free_order_amount")
    private BigDecimal freeOrderAmount;

    @TableField("create_time")
    private LocalDateTime createTime;
}