package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("res_daily_product_stats")
public class ResDailyProductStats {

    @TableId
    private String id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("fid")
    private String fid;

    @TableField("fname")
    private String fname;

    @TableField("category")
    private String category;

    @TableField("pv_count")
    private Integer pvCount;

    @TableField("uv_count")
    private Integer uvCount;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("order_quantity")
    private Integer orderQuantity;

    @TableField("sales_amount")
    private BigDecimal salesAmount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("dislike_count")
    private Integer dislikeCount;

    @TableField("review_count")
    private Integer reviewCount;

    @TableField("avg_star")
    private BigDecimal avgStar;

    @TableField("conversion_rate")
    private BigDecimal conversionRate;

    /** 购物车添加次数 */
    @TableField("cart_count")
    private Integer cartCount;

    /** 已付款订单数 */
    @TableField("paid_order_count")
    private Integer paidOrderCount;

    @TableField("create_time")
    private LocalDateTime createTime;
}