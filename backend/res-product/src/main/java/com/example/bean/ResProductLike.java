package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("res_product_like")
public class ResProductLike {

    @TableId("id")
    private String id;

    @TableField("fid")
    private String fid;

    @TableField("user_id")
    private String userId;

    @TableField("like_type")
    private Integer likeType;

    @TableField("order_id")
    private String orderId;

    @TableField("create_time")
    private LocalDateTime createTime;
}