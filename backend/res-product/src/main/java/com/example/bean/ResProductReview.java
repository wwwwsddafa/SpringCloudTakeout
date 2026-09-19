package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_product_review")
public class ResProductReview {

    @TableId("review_id")
    private String reviewId;

    @TableField("fid")
    private String fid;

    @TableField("user_id")
    private String userId;

    @TableField("order_id")
    private String orderId;

    @TableField("star_rating")
    private Integer starRating;

    @TableField("review_text")
    private String reviewText;

    @TableField("review_images")
    private String reviewImages;

    @TableField("status")
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}