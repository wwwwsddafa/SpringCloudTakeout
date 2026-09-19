package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("res_daily_ops_summary")
public class ResDailyOpsSummary {

    @TableId
    private String id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("total_pv")
    private Long totalPv;

    @TableField("total_uv")
    private Long totalUv;

    @TableField("new_users")
    private Integer newUsers;

    @TableField("active_users")
    private Integer activeUsers;

    @TableField("total_orders")
    private Integer totalOrders;

    @TableField("total_revenue")
    private BigDecimal totalRevenue;

    @TableField("conversion_rate")
    private BigDecimal conversionRate;

    @TableField("avg_order_amount")
    private BigDecimal avgOrderAmount;

    @TableField("total_likes")
    private Integer totalLikes;

    @TableField("total_reviews")
    private Integer totalReviews;

    @TableField("top_product_ids")
    private String topProductIds;

    @TableField("top_search_keywords")
    private String topSearchKeywords;

    @TableField("hourly_pv_json")
    private String hourlyPvJson;

    @TableField("hourly_order_json")
    private String hourlyOrderJson;

    @TableField("device_dist_json")
    private String deviceDistJson;

    @TableField("browser_dist_json")
    private String browserDistJson;

    @TableField("create_time")
    private LocalDateTime createTime;
}