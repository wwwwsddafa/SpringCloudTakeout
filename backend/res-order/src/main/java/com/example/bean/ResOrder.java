package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resorder")
public class ResOrder {

    @TableId
    private String roid;

    private String userid;

    private String uname;

    private String address;

    private String tel;

    @TableField("ordertime")
    private LocalDateTime orderTime;

    @TableField("deliverytime")
    private LocalDateTime deliveryTime;

    @TableField("deliverytype")
    private String deliveryType;

    private String payment;

    private String ps;

    private Integer status;

    private String tradeno;

    @TableField("paytime")
    private LocalDateTime payTime;

    @TableField("cancel_time")
    private LocalDateTime cancelTime;

    @TableField("total_amount")
    private java.math.BigDecimal totalAmount;

    @TableField("discount_amount")
    private java.math.BigDecimal discountAmount;

    @TableField("pay_amount")
    private java.math.BigDecimal payAmount;
}