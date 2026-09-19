package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("res_free_order_record")
public class ResFreeOrderRecord {

    @TableId("record_id")
    private String recordId;

    @TableField("event_id")
    private String eventId;

    @TableField("user_id")
    private String userId;

    @TableField("grab_time")
    private LocalDateTime grabTime;

    @TableField("free_order_no")
    private String freeOrderNo;

    @TableField("coupon_amount")
    private BigDecimal couponAmount;

    private Integer used;

    @TableField("used_order_id")
    private String usedOrderId;

    @TableField("used_amount")
    private BigDecimal usedAmount;
}